package io.softa.framework.orm.vo;

import java.io.Serial;
import java.io.Serializable;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * Option reference object.
 * Used to reference the option item code, label, tone, and icon.
 */
@Data
@Schema(name = "OptionReference")
public class OptionReference implements Serializable  {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "Option Item Label")
    private String label;

    @Schema(description = "Option Item Code")
    private String itemCode;

    @Schema(description = "Option Item Tone")
    private String itemTone;

    @Schema(description = "Option Item Icon")
    private String itemIcon;

    /**
     * Create an OptionReference object.
     *
     * @param itemCode Option item code
     * @param label Option item label
     * @return OptionReference object
     */
    static public OptionReference of(String itemCode, String label) {
        OptionReference optionReference = new OptionReference();
        optionReference.setItemCode(itemCode);
        optionReference.setLabel(label);
        return optionReference;
    }

    /**
     * Create an OptionReference object.
     *
     * @param itemCode Option item code
     * @param label Option item label
     * @param itemTone Option item tone
     * @param itemIcon Option item icon
     * @return OptionReference object
     */
    static public OptionReference of(String itemCode, String label, String itemTone, String itemIcon) {
        OptionReference optionReference = OptionReference.of(itemCode, label);
        optionReference.setItemTone(itemTone);
        optionReference.setItemIcon(itemIcon);
        return optionReference;
    }
}
