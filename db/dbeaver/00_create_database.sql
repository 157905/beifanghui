-- DBeaver 连接 MySQL 8.0+ 后执行。生产环境应用账号不得长期使用 root。
CREATE DATABASE IF NOT EXISTS beifanghui
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_0900_ai_ci;
USE beifanghui;

-- 新环境随后依次执行：
-- 1. ../../src/main/resources/db/migration/V1__init_core_schema.sql
-- 2. ../../src/main/resources/db/migration/V2__extend_platform_schema.sql
-- 3. ../../src/main/resources/db/migration/V3__add_business_domain_schema.sql
