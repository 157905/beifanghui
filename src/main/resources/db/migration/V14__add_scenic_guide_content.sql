-- 景区导览内容：地图、景点介绍、语音讲解和游玩攻略均可由管理端维护。
CREATE TABLE bf_scenic_guide_content (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    scenic_spot_id BIGINT NOT NULL,
    content_type VARCHAR(32) NOT NULL,
    title VARCHAR(200) NOT NULL,
    summary VARCHAR(500) NULL,
    cover_url VARCHAR(512) NULL,
    content_url VARCHAR(512) NULL,
    content_text TEXT NULL,
    duration_seconds INT NULL,
    sort_order INT NOT NULL DEFAULT 0,
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_scenic_guide_public (scenic_spot_id, status, content_type, sort_order),
    CONSTRAINT fk_scenic_guide_spot FOREIGN KEY (scenic_spot_id) REFERENCES bf_resource(id),
    CONSTRAINT chk_scenic_guide_type CHECK (content_type IN ('MAP','ATTRACTION','AUDIO','STRATEGY')),
    CONSTRAINT chk_scenic_guide_status CHECK (status IN ('DRAFT','ACTIVE','INACTIVE')),
    CONSTRAINT chk_scenic_guide_duration CHECK (duration_seconds IS NULL OR duration_seconds >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='景区导览内容';

-- 开发联调用导览数据；正式图文、音频版权及地图数据应由运营方维护。
INSERT INTO bf_scenic_guide_content
(scenic_spot_id, content_type, title, summary, content_text, duration_seconds, sort_order, status)
SELECT id, 'MAP', '北方兵器城导览地图', '入口、展馆和服务点示意。', '入口—兵器历史馆—装备展区—纪念品服务点。', NULL, 1, 'ACTIVE'
FROM bf_resource WHERE name='北方兵器城' AND resource_type='SCENIC_TICKET'
UNION ALL
SELECT id, 'ATTRACTION', '兵器历史馆', '了解兵器工业发展历程。', '建议游览45分钟，馆内请勿触摸展品。', NULL, 2, 'ACTIVE'
FROM bf_resource WHERE name='北方兵器城' AND resource_type='SCENIC_TICKET'
UNION ALL
SELECT id, 'AUDIO', '兵器历史馆语音讲解', '馆内重点展品讲解。', '语音讲解文本仅供开发联调。', 180, 3, 'ACTIVE'
FROM bf_resource WHERE name='北方兵器城' AND resource_type='SCENIC_TICKET'
UNION ALL
SELECT id, 'STRATEGY', '北方兵器城半日游攻略', '推荐按展馆顺序游览。', '建议上午入园，预留2至3小时游览时间。', NULL, 4, 'ACTIVE'
FROM bf_resource WHERE name='北方兵器城' AND resource_type='SCENIC_TICKET';
