package com.beifanghui.backend.scenic;

import com.beifanghui.backend.identity.web.AuthenticatedPrincipal;
import com.beifanghui.backend.scenic.api.AdminScenicSpotCreateRequest;
import com.beifanghui.backend.scenic.api.AdminScenicSpotResponse;
import com.beifanghui.backend.scenic.api.ScenicGuideResponse;
import com.beifanghui.backend.scenic.api.ScenicGuideUpsertRequest;
import com.beifanghui.backend.scenic.application.AdminScenicManagementService;
import com.beifanghui.backend.scenic.application.ScenicGuideService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, properties = {
        "spring.datasource.url=jdbc:mysql://localhost:3306/beifanghui_test?createDatabaseIfNotExist=true&useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai",
        "spring.task.scheduling.enabled=false"
})
@Transactional
class ScenicGuideIT {
    private static final AuthenticatedPrincipal ADMIN = new AuthenticatedPrincipal(
            "guide-admin", "导览管理员", "ADMIN", List.of("ROLE_ADMIN"));
    @Autowired AdminScenicManagementService scenicService;
    @Autowired ScenicGuideService guideService;

    @Test
    void 只有已发布导览内容对用户可见() {
        AdminScenicSpotResponse spot = scenicService.createScenicSpot(ADMIN, new AdminScenicSpotCreateRequest(
                "IT_GUIDE_SCENIC", "导览测试景区", "TEST", "测试", null, "测试地址", null, null,
                "0472-2000000", "测试", "09:00-17:00", 60, "ACTIVE"));
        ScenicGuideResponse guide = guideService.create(ADMIN, spot.id(), new ScenicGuideUpsertRequest(
                "AUDIO", "测试语音讲解", "测试摘要", null, "/audio/test.mp3", "讲解文本", 60, 1, "ACTIVE"));
        assertEquals(1, guideService.listPublic(spot.id(), "AUDIO").size());
        guideService.update(ADMIN, guide.id(), new ScenicGuideUpsertRequest(
                "AUDIO", "测试语音讲解", "测试摘要", null, "/audio/test.mp3", "讲解文本", 60, 1, "INACTIVE"));
        assertTrue(guideService.listPublic(spot.id(), "AUDIO").isEmpty());
        assertEquals(1, guideService.listAdmin(ADMIN, spot.id()).size());
    }
}
