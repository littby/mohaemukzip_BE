# 운영(Prod) DB 마이그레이션

`fix/289-chatbor-bug` 브랜치 배포 전, 아래 SQL을 운영 DB에 **먼저** 실행해야 합니다. 이 프로젝트는 Flyway/Liquibase 없이 `ddl-auto`로 스키마를 관리하며 운영은 `validate` 모드라, 스키마가 안 맞으면 배포 시 앱이 기동 실패합니다.

## 실행할 SQL (전체)

```sql
-- ============================================
-- 1) 챗봇 대화 로그 테이블 신규 생성 (필수)
-- ============================================
CREATE TABLE `chat_logs` (
  `chat_log_id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `bot_message` text,
  `bot_title` varchar(255) DEFAULT NULL,
  `user_message` text NOT NULL,
  `member_id` bigint NOT NULL,
  PRIMARY KEY (`chat_log_id`),
  KEY `FKm23b9liy3c3ynhm6jar7na59o` (`member_id`),
  CONSTRAINT `FKm23b9liy3c3ynhm6jar7na59o` FOREIGN KEY (`member_id`) REFERENCES `members` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `chat_log_recipe_ids` (
  `chat_log_id` bigint NOT NULL,
  `recipe_id` bigint DEFAULT NULL,
  KEY `FKpxl7ks3whoo226dh4j7i5eqd9` (`chat_log_id`),
  CONSTRAINT `FKpxl7ks3whoo226dh4j7i5eqd9` FOREIGN KEY (`chat_log_id`) REFERENCES `chat_logs` (`chat_log_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ============================================
-- 2) 코드와 무관한 유령 테이블 정리 (안전 확인 완료, 필수는 아니지만 함께 진행하기로 결정)
-- ============================================
SET FOREIGN_KEY_CHECKS = 0;
DROP TABLE IF EXISTS chat_messages;
DROP TABLE IF EXISTS chat_rooms;
DROP TABLE IF EXISTS member_cook_histories;
DROP TABLE IF EXISTS recipe_categories;
SET FOREIGN_KEY_CHECKS = 1;
```

## 실행 후 확인

```sql
SHOW TABLES;
```
- `chat_logs`, `chat_log_recipe_ids`가 생겼는지
- `chat_messages`, `chat_rooms`, `member_cook_histories`, `recipe_categories`가 없어졌는지

확인되면 → PR #290을 `main`에 머지 → GitHub Actions가 자동 빌드 후 EC2에 배포.

---

## 참고: 각 항목을 이렇게 결정한 이유

<details>
<summary>펼쳐서 보기</summary>

**1) `chat_logs`/`chat_log_recipe_ids`**: 회원별 챗봇 질문/답변을 영구 기록하기 위한 신규 테이블. 로컬 dev DB에 `ddl-auto=update`를 실제로 실행시켜 Hibernate가 생성한 결과를 그대로 추출했습니다(추측 아님). 최초엔 `session_id` 컬럼도 있었으나, `sessionId` 개념 자체를 폐기하고 `memberId` + Redis 30분 TTL로만 관리하기로 하면서 제거했습니다.

**2) `members.email`/`terms_agreed` 컬럼**: 로컬 dev DB에는 없어서 우려했지만, 2026-07-30 운영 DB 직접 확인 결과 이미 존재함을 확인. 로컬 dev DB만 리셋되어 뒤처져 있었던 것 — 운영은 조치 불필요.

**3) `recipes.embedding` 컬럼**: 챗봇의 벡터 임베딩 검색 기능을 이번에 전부 삭제했지만, DB 컬럼 자체(`recipes.embedding`)는 지우지 않고 Java 매핑만 제거했습니다. `ddl-auto=validate`는 매핑 안 된 여분 컬럼을 문제 삼지 않아 조치 불필요. 2026-07-30 운영 DB 확인으로 컬럼 실존을 재확인.

**4) 유령 테이블 4개 삭제**: `chat_rooms`/`chat_messages`(과거 Redis 전환 시 삭제된 엔티티의 잔재), `member_cook_histories`/`recipe_categories`(그보다 이전 리네이밍 흔적, 현재는 각각 `cooking_records`/`dish_categories`로 대체 추정) — 코드 전체 검색 결과 어떤 엔티티도 매핑하지 않음. 삭제 전 안전 확인(2026-07-30, 운영 DB 직접 조회):
  - 4개 테이블 전부 row count 0건(실 데이터 없음)
  - 다른 살아있는 테이블이 이 4개를 참조(FK)하는 경우 없음(`chat_messages→chat_rooms`는 삭제 대상끼리의 내부 관계일 뿐)
  - 이 4개가 참조하는 대상(`members`/`recipes`/`categories`)은 전부 살아있는 테이블이라 삭제해도 안전

</details>
