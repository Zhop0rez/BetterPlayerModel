package com.elfmcys.yesstevemodel.geckolib3.core.keyframe.bone;

import it.unimi.dsi.fastutil.objects.ReferenceArrayList;

import java.util.List;

public class BoneKeyFrameProcessor {
    public static List<BoneKeyFrame> process(RawBoneKeyFrame[] frames, boolean isRotation) {
        return process(ReferenceArrayList.wrap(frames), isRotation);
    }

    public static List<BoneKeyFrame> process(List<RawBoneKeyFrame> frames, boolean isRotation) {
        for (RawBoneKeyFrame frame : frames) {
            frame.init(isRotation);
        }
        BoneKeyFrame[] list = new BoneKeyFrame[frames.size()];
        for (int i = 0; i < frames.size(); i++) {
            RawBoneKeyFrame end = frames.get(i);
            // и™Ѕз„¶ж„џи§‰дёЌе¤Єеђ€зђ†пјЊдЅ†иїжЇе’Њ BlockBench дїќжЊЃдёЂи‡ґжЇ”иѕѓеҐЅ
            EasingType easingType;
            if (end.easingType() == EasingType.CATMULLROM || i == 0) {
                easingType = end.easingType();
            } else {
                easingType = frames.get(i - 1).easingType();
            }
            list[i] = easingType.buildKeyFrame(frames, i);
        }
        return ReferenceArrayList.wrap(list);
    }
}
