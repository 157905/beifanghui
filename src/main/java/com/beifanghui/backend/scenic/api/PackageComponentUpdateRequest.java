package com.beifanghui.backend.scenic.api;

import java.util.List;

public record PackageComponentUpdateRequest(List<PackageComponentRequest> components) {
}
