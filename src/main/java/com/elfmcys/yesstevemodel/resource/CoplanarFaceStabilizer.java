package com.elfmcys.yesstevemodel.resource;

import com.elfmcys.yesstevemodel.geckolib3.geo.render.built.GeoModel;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Separates overlapping faces that share one bone so animation cannot make the
 * depth buffer alternate between them. Faces on different bones are left alone
 * because their relative position may be intentional and can change over time.
 */
final class CoplanarFaceStabilizer {
    private static final float PARALLEL_DOT = 0.9999f;
    private static final float PLANE_EPSILON = 0.00001f;
    private static final float OVERLAP_EPSILON = 0.000001f;
    private static final float MIN_OVERLAP_COVERAGE = 0.02f;
    private static final float LAYER_STEP = 0.0002f;
    private static final int MAX_PASSES = 4;

    private CoplanarFaceStabilizer() {
    }

    static int stabilize(List<GeoModel.BakedBone> bones) {
        int shiftedFaces = 0;
        for (GeoModel.BakedBone bone : bones) {
            for (int pass = 0; pass < MAX_PASSES; pass++) {
                int shiftedThisPass = stabilizeBone(bone);
                shiftedFaces += shiftedThisPass;
                if (shiftedThisPass == 0) {
                    break;
                }
            }
        }
        return shiftedFaces;
    }

    private static int stabilizeBone(GeoModel.BakedBone bone) {
        List<FaceRef> faces = collectFaces(bone);
        if (faces.size() < 2) {
            return 0;
        }

        DisjointSet conflicts = new DisjointSet(faces.size());
        boolean foundConflict = false;
        for (int leftIndex = 0; leftIndex < faces.size(); leftIndex++) {
            FaceRef left = faces.get(leftIndex);
            if (!left.valid) {
                continue;
            }
            for (int rightIndex = leftIndex + 1; rightIndex < faces.size(); rightIndex++) {
                FaceRef right = faces.get(rightIndex);
                if (!right.valid || left.cubeIndex == right.cubeIndex) {
                    continue;
                }
                if (isSignificantCoplanarOverlap(left, right)) {
                    conflicts.union(leftIndex, rightIndex);
                    foundConflict = true;
                }
            }
        }

        if (!foundConflict) {
            return 0;
        }

        Map<Integer, List<FaceRef>> groups = new HashMap<>();
        for (int index = 0; index < faces.size(); index++) {
            int root = conflicts.find(index);
            if (conflicts.size(root) > 1) {
                groups.computeIfAbsent(root, ignored -> new ArrayList<>()).add(faces.get(index));
            }
        }

        int shiftedFaces = 0;
        for (List<FaceRef> group : groups.values()) {
            group.sort(Comparator.comparingInt(face -> face.ordinal));
            Vector3f axis = canonicalNormal(group.get(0).normal);
            List<FaceRef> positiveFacing = new ArrayList<>();
            List<FaceRef> negativeFacing = new ArrayList<>();
            for (FaceRef face : group) {
                if (face.normal.dot(axis) >= 0.0f) {
                    positiveFacing.add(face);
                } else {
                    negativeFacing.add(face);
                }
            }

            boolean hasOppositeFacings = !positiveFacing.isEmpty() && !negativeFacing.isEmpty();
            shiftedFaces += separateFacingLayers(positiveFacing, hasOppositeFacings);
            shiftedFaces += separateFacingLayers(negativeFacing, hasOppositeFacings);
        }
        return shiftedFaces;
    }

    private static int separateFacingLayers(List<FaceRef> faces, boolean hasOppositeFacings) {
        if (faces.isEmpty()) {
            return 0;
        }

        // Gecko/OpenYSM preserves authored cube order. The earlier face is the
        // intended visible layer. Mirror-safe offsets therefore follow each
        // face's own outward normal instead of a world-axis sign.
        float firstLayer = hasOppositeFacings
                ? faces.size() - 0.5f
                : (faces.size() - 1) * 0.5f;
        for (int index = 0; index < faces.size(); index++) {
            FaceRef face = faces.get(index);
            float offset = (firstLayer - index) * LAYER_STEP;
            translate(face.quad, face.normal, offset);
        }
        return faces.size();
    }

    private static List<FaceRef> collectFaces(GeoModel.BakedBone bone) {
        List<FaceRef> faces = new ArrayList<>();
        int ordinal = 0;
        for (int cubeIndex = 0; cubeIndex < bone.cubes.size(); cubeIndex++) {
            GeoModel.BakedCube cube = bone.cubes.get(cubeIndex);
            for (GeoModel.BakedQuad quad : cube.quads) {
                faces.add(new FaceRef(cubeIndex, ordinal++, quad));
            }
        }
        return faces;
    }

    private static boolean isSignificantCoplanarOverlap(FaceRef left, FaceRef right) {
        if (Math.abs(left.normal.dot(right.normal)) < PARALLEL_DOT) {
            return false;
        }

        float separation = Math.abs(new Vector3f(right.center).sub(left.center).dot(left.normal));
        if (separation > PLANE_EPSILON) {
            return false;
        }

        int droppedAxis = dominantAxis(left.normal);
        float[][] leftPolygon = left.project(droppedAxis);
        float[][] rightPolygon = right.project(droppedAxis);
        if (hasSeparatingAxis(leftPolygon, rightPolygon)
                || hasSeparatingAxis(rightPolygon, leftPolygon)) {
            return false;
        }

        float smallerArea = Math.min(polygonArea(leftPolygon), polygonArea(rightPolygon));
        if (smallerArea <= OVERLAP_EPSILON) {
            return false;
        }

        float overlapArea = convexIntersectionArea(leftPolygon, rightPolygon);
        return overlapArea > OVERLAP_EPSILON
                && overlapArea / smallerArea >= MIN_OVERLAP_COVERAGE;
    }

    private static int dominantAxis(Vector3f normal) {
        float x = Math.abs(normal.x);
        float y = Math.abs(normal.y);
        float z = Math.abs(normal.z);
        return x >= y && x >= z ? 0 : y >= z ? 1 : 2;
    }

    private static boolean hasSeparatingAxis(float[][] source, float[][] other) {
        for (int index = 0; index < source.length; index++) {
            float[] first = source[index];
            float[] second = source[(index + 1) % source.length];
            float axisX = first[1] - second[1];
            float axisY = second[0] - first[0];
            float axisLength = (float) Math.sqrt(axisX * axisX + axisY * axisY);
            if (axisLength <= OVERLAP_EPSILON) {
                continue;
            }
            axisX /= axisLength;
            axisY /= axisLength;

            float[] sourceRange = projectionRange(source, axisX, axisY);
            float[] otherRange = projectionRange(other, axisX, axisY);
            float overlap = Math.min(sourceRange[1], otherRange[1])
                    - Math.max(sourceRange[0], otherRange[0]);
            if (overlap <= OVERLAP_EPSILON) {
                return true;
            }
        }
        return false;
    }

    private static float[] projectionRange(float[][] polygon, float axisX, float axisY) {
        float min = polygon[0][0] * axisX + polygon[0][1] * axisY;
        float max = min;
        for (int index = 1; index < polygon.length; index++) {
            float value = polygon[index][0] * axisX + polygon[index][1] * axisY;
            min = Math.min(min, value);
            max = Math.max(max, value);
        }
        return new float[]{min, max};
    }

    private static float convexIntersectionArea(float[][] subject, float[][] clip) {
        List<float[]> output = new ArrayList<>(subject.length);
        for (float[] point : subject) {
            output.add(new float[]{point[0], point[1]});
        }

        float orientation = signedPolygonArea(clip) >= 0.0f ? 1.0f : -1.0f;
        for (int edge = 0; edge < clip.length && !output.isEmpty(); edge++) {
            float[] edgeStart = clip[edge];
            float[] edgeEnd = clip[(edge + 1) % clip.length];
            List<float[]> input = output;
            output = new ArrayList<>();
            float[] previous = input.get(input.size() - 1);
            boolean previousInside = inside(previous, edgeStart, edgeEnd, orientation);
            for (float[] current : input) {
                boolean currentInside = inside(current, edgeStart, edgeEnd, orientation);
                if (currentInside != previousInside) {
                    output.add(lineIntersection(previous, current, edgeStart, edgeEnd));
                }
                if (currentInside) {
                    output.add(current);
                }
                previous = current;
                previousInside = currentInside;
            }
        }
        return output.size() < 3 ? 0.0f : polygonArea(output.toArray(new float[0][]));
    }

    private static boolean inside(
            float[] point, float[] edgeStart, float[] edgeEnd, float orientation) {
        float cross = (edgeEnd[0] - edgeStart[0]) * (point[1] - edgeStart[1])
                - (edgeEnd[1] - edgeStart[1]) * (point[0] - edgeStart[0]);
        return cross * orientation >= -OVERLAP_EPSILON;
    }

    private static float[] lineIntersection(
            float[] lineStart, float[] lineEnd, float[] edgeStart, float[] edgeEnd) {
        float lineX = lineEnd[0] - lineStart[0];
        float lineY = lineEnd[1] - lineStart[1];
        float edgeX = edgeEnd[0] - edgeStart[0];
        float edgeY = edgeEnd[1] - edgeStart[1];
        float denominator = lineX * edgeY - lineY * edgeX;
        if (Math.abs(denominator) <= OVERLAP_EPSILON) {
            return new float[]{lineEnd[0], lineEnd[1]};
        }
        float offsetX = edgeStart[0] - lineStart[0];
        float offsetY = edgeStart[1] - lineStart[1];
        float factor = (offsetX * edgeY - offsetY * edgeX) / denominator;
        return new float[]{lineStart[0] + factor * lineX, lineStart[1] + factor * lineY};
    }

    private static float polygonArea(float[][] polygon) {
        return Math.abs(signedPolygonArea(polygon));
    }

    private static float signedPolygonArea(float[][] polygon) {
        float twiceArea = 0.0f;
        for (int index = 0; index < polygon.length; index++) {
            float[] current = polygon[index];
            float[] next = polygon[(index + 1) % polygon.length];
            twiceArea += current[0] * next[1] - next[0] * current[1];
        }
        return twiceArea * 0.5f;
    }

    private static Vector3f canonicalNormal(Vector3f source) {
        Vector3f result = new Vector3f(source);
        if (result.x < 0.0f
                || result.x == 0.0f && result.y < 0.0f
                || result.x == 0.0f && result.y == 0.0f && result.z < 0.0f) {
            result.negate();
        }
        return result;
    }

    private static void translate(GeoModel.BakedQuad quad, Vector3f axis, float offset) {
        float x = axis.x * offset;
        float y = axis.y * offset;
        float z = axis.z * offset;
        for (Vector3f position : quad.positions) {
            position.add(x, y, z);
        }
    }

    private static final class FaceRef {
        private final int cubeIndex;
        private final int ordinal;
        private final GeoModel.BakedQuad quad;
        private final Vector3f normal;
        private final Vector3f center;
        private final float[][][] projections = new float[3][][];
        private final boolean valid;

        private FaceRef(int cubeIndex, int ordinal, GeoModel.BakedQuad quad) {
            this.cubeIndex = cubeIndex;
            this.ordinal = ordinal;
            this.quad = quad;
            this.normal = new Vector3f(quad.normal);
            this.valid = normal.lengthSquared() > OVERLAP_EPSILON * OVERLAP_EPSILON;
            if (valid) {
                normal.normalize();
            }
            this.center = new Vector3f();
            for (Vector3f position : quad.positions) {
                center.add(position);
            }
            center.mul(0.25f);
        }

        private float[][] project(int droppedAxis) {
            float[][] cached = projections[droppedAxis];
            if (cached != null) {
                return cached;
            }
            float[][] result = new float[quad.positions.length][2];
            for (int index = 0; index < quad.positions.length; index++) {
                Vector3f position = quad.positions[index];
                if (droppedAxis == 0) {
                    result[index][0] = position.y;
                    result[index][1] = position.z;
                } else if (droppedAxis == 1) {
                    result[index][0] = position.x;
                    result[index][1] = position.z;
                } else {
                    result[index][0] = position.x;
                    result[index][1] = position.y;
                }
            }
            projections[droppedAxis] = result;
            return result;
        }
    }

    private static final class DisjointSet {
        private final int[] parent;
        private final int[] sizes;

        private DisjointSet(int size) {
            parent = new int[size];
            sizes = new int[size];
            for (int index = 0; index < size; index++) {
                parent[index] = index;
                sizes[index] = 1;
            }
        }

        private int find(int value) {
            int root = value;
            while (root != parent[root]) {
                root = parent[root];
            }
            while (value != root) {
                int next = parent[value];
                parent[value] = root;
                value = next;
            }
            return root;
        }

        private void union(int left, int right) {
            int leftRoot = find(left);
            int rightRoot = find(right);
            if (leftRoot == rightRoot) {
                return;
            }
            if (sizes[leftRoot] < sizes[rightRoot]) {
                int swap = leftRoot;
                leftRoot = rightRoot;
                rightRoot = swap;
            }
            parent[rightRoot] = leftRoot;
            sizes[leftRoot] += sizes[rightRoot];
        }

        private int size(int value) {
            return sizes[find(value)];
        }
    }
}
