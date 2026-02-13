package com.bulc.homepage.licensing.dto;

import java.util.List;

public record RedeemCodeGenerateResponse(
        int generatedCount,
        List<String> codes
) {
}
