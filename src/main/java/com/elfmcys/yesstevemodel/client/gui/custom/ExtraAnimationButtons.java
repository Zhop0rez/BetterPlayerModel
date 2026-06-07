package com.elfmcys.yesstevemodel.client.gui.custom;

public class ExtraAnimationButtons {
    //   "extra_animation_buttons": [
    //      {
    //        "id": "extra_config",
    //        "name": "0",
    //        "config_forms": [
    //          {
    //            "type": "checkbox",
    //            "title": "headdress/е¤ґйҐ°",
    //            "description": "Used to hide/show the red bow headdress (з”ЁжќҐжѕз¤єж€–ејЂеђЇзЋ©е®¶е¤ґйҐ°)",
    //            "value": "v.roaming.red_bow_headdress"
    //          }
    //        ]
    //      }
    //    ],

    private final String id; // id

    private final String name; // name

    private final String description; // еҐЅеѓЏдёЌе­ењЁ

    private final AbstractConfig[] configForms;

    public ExtraAnimationButtons(String id, String name, String description, AbstractConfig[] configForms) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.configForms = configForms;
    }

    public String getId() {
        return this.id;
    }

    public String getName() {
        return this.name;
    }

    public String getDescription() {
        return this.description;
    }

    public AbstractConfig[] getConfigForms() {
        return this.configForms;
    }
}
