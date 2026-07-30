package com.beifanghui.backend.scenic.api;

public record ScenicVisitorResponse(long id, long orderItemId, String personType,
                                    String maskedName, String maskedMobile,
                                    String idType, String maskedIdNo) {}
