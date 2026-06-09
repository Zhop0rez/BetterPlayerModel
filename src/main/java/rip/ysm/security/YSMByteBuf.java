package rip.ysm.security;

import io.netty.buffer.ByteBuf;

import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

public class YSMByteBuf implements AutoCloseable {
    private static final int MAX_BYTE_ARRAY_BYTES = 128 * 1024 * 1024;
    private static final int MAX_STRING_BYTES = 1 * 1024 * 1024;

    private final ByteBuf buf;

    public YSMByteBuf(ByteBuf buf) {
        this.buf = buf.order(ByteOrder.LITTLE_ENDIAN); // YSMзљ„еџєж–јC++пјЊдЅїз”Ёе°Џз«ЇеєЏ
    }

    public ByteBuf getRawBuf() { return this.buf; }

    // ж¶€иІ»ећѓењѕж•ёж“љй ­йѓЁпјЊйІж­ўи®ЂеЇ«е‡єе•ЏйЎЊ
    public int skipGarbageHeader() {
        if (buf.readableBytes() < 1) return 0;
        int garbageLen = buf.readByte() & 0x7F;
        if (buf.readableBytes() < garbageLen + 1) {
            buf.readerIndex(buf.writerIndex()); // skip everything
            return garbageLen;
        }
        buf.skipBytes(1); // skip 0x00
        buf.skipBytes(garbageLen);
        return garbageLen;
    }

    public void writeGarbageHeader(int garbageLen, byte[] garbageData) {
        buf.writeByte(garbageLen | 0x80);
        buf.writeByte(0x00);
        buf.writeBytes(garbageData);
    }

    public int getOffset() {
        return buf.readerIndex();
    }

    public void setOffset(int offset) {
        buf.readerIndex(offset);
    }

    public byte readByte() {
        return buf.readByte();
    }

    public float readFloat() {
        return buf.readFloat();
    }

    public long readDword() {
        return buf.readUnsignedInt();
    }

    public void writeDword(int format) {
        buf.writeInt(format);
    }

    public int readVarInt() {
        int value = 0;
        int position = 0;
        while (true) {
            byte currentByte = buf.readByte();
            value |= (currentByte & 0x7F) << position;
            if ((currentByte & 0x80) == 0) break;
            position += 7;
            if (position >= 64) throw new RuntimeException("VarInt too big");
        }
        return value;
    }

    public void writeVarInt(int value) {
        while ((value & -128) != 0) {
            buf.writeByte(value & 127 | 128);
            value >>>= 7;
        }
        buf.writeByte(value);
    }

    public long readVarLong() {
        long value = 0L;
        int position = 0;
        while (true) {
            byte currentByte = buf.readByte();
            value |= (long) (currentByte & 0x7F) << position;
            if ((currentByte & 0x80) == 0) break;
            position += 7;
            if (position >= 64) throw new RuntimeException("VarLong too big");
        }
        return value;
    }

    public void writeVarLong(long value) {
        while ((value & -128L) != 0L) {
            buf.writeByte((int) (value & 127L) | 128);
            value >>>= 7;
        }
        buf.writeByte((int) value);
    }

    public byte[] readByteArray() {
        int len = readVarInt();
        validateReadableLength(len, MAX_BYTE_ARRAY_BYTES, "byte array");
        if (len == 0) return new byte[0];
        byte[] bytes = new byte[len];
        buf.readBytes(bytes);
        return bytes;
    }

    public String readString() {
        int len = readVarInt();
        validateReadableLength(len, MAX_STRING_BYTES, "string");
        if (len == 0) return "";
        byte[] bytes = new byte[len];
        buf.readBytes(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private void validateReadableLength(int len, int maxLen, String type) {
        if (len < 0) {
            throw new IllegalArgumentException("Negative " + type + " length: " + len);
        }
        if (len > maxLen) {
            throw new IllegalArgumentException(type + " length exceeds limit: " + len + " > " + maxLen);
        }
        if (len > buf.readableBytes()) {
            throw new IllegalArgumentException(type + " length exceeds readable bytes: " + len + " > " + buf.readableBytes());
        }
    }

    public byte[] toArray() {
        byte[] bytes = new byte[buf.readableBytes()];
        buf.readBytes(bytes);
        return bytes;
    }

    public void skipBytes(int n) {
        buf.skipBytes(n);
    }

    public void writeString(String s) {
        if (s == null || s.isEmpty()) {
            writeVarInt(0);
            return;
        }
        byte[] bytes = s.getBytes(StandardCharsets.UTF_8);
        writeVarInt(bytes.length);
        buf.writeBytes(bytes);
    }

    public void writeByte(byte value) {
        buf.writeByte(value);
    }

    public void writeFloat(float value) {
        buf.writeFloat(value);
    }

    public void writeByteArray(byte[] data) {
        if (data == null || data.length == 0) {
            writeVarInt(0);
            return;
        }
        writeVarInt(data.length);
        buf.writeBytes(data);
    }

    public void writeByteBuf(ByteBuf other) {
        buf.writeBytes(other);
    }

    public void release() {
        if (this.buf != null && this.buf.refCnt() > 0) {
            this.buf.release();
        }
    }

    @Override
    public void close() {
        release();
    }
}
