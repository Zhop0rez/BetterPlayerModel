package com.elfmcys.yesstevemodel.model.format;

import com.elfmcys.yesstevemodel.resource.models.Metadata;
import com.elfmcys.yesstevemodel.resource.models.ModelProperties;
import com.elfmcys.yesstevemodel.util.FileTypeUtil;
import com.elfmcys.yesstevemodel.resource.models.MainModelInfo;
import org.jetbrains.annotations.Nullable;

public class ServerModelInfo {

    @Nullable
    private final Metadata metadata;

    private final ModelProperties modelProperties;

    private final MainModelInfo mainModelInfo;

    private final int formatVersion; // .ysmзљ„е†…йѓЁзљ„formatз‰€жњ¬еЏ· жІЎжњ‰е°±жЇ65535

    private final String modelHash;

    private final String extra; // е¤‡жіЁ

    private final long timestamp;

    private final String rand;

    private final int hashId;

    public ServerModelInfo(@Nullable Metadata metadata, ModelProperties modelProperties, MainModelInfo mainModelInfo, int formatVersion, String modelHash, String extra, long timestamp, String rand) {
        this.metadata = metadata;
        this.modelProperties = modelProperties;
        this.mainModelInfo = mainModelInfo;
        this.formatVersion = formatVersion;
        this.modelHash = modelHash;
        this.extra = extra;
        this.timestamp = timestamp;
        this.rand = rand;
        this.hashId = FileTypeUtil.parseHexId(modelHash);
    }

    @Nullable
    public Metadata getExtraInfo() {
        return this.metadata;
    }

    public ModelProperties getModelProperties() {
        return this.modelProperties;
    }

    public MainModelInfo getMainModelInfo() {
        return this.mainModelInfo;
    }

    public int getFormatVersion() {
        return this.formatVersion;
    }

    public String getModelHash() {
        return this.modelHash;
    }

    public String getExtra() {
        return this.extra;
    }

    public long getTimestamp() {
        return this.timestamp;
    }

    public String getRand() {
        return this.rand;
    }

    public int getHashId() {
        return this.hashId;
    }
}
