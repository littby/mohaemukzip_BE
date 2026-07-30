# 운영(Prod) DB 반영 필요 사항 정리

이 문서는 `fix/289-chatbor-bug` 브랜치 작업(챗봇 리팩토링 세션) 중 발생한 DB 스키마 변경 사항을 정리한 것입니다. 이 프로젝트는 Flyway/Liquibase 없이 `spring.jpa.hibernate.ddl-auto`로 스키마를 관리하며, **dev는 `update`(자동 반영), prod는 `validate`(엔티티와 DB 스키마가 다르면 기동 실패)** 입니다. 따라서 아래 DDL은 **앱 배포 전에 반드시 운영 DB에 먼저 적용**해야 합니다.

모든 DDL은 로컬 dev DB(`mohaemukzip-mysql` 컨테이너)에 `ddl-auto=update`를 실제로 실행시켜 Hibernate가 생성한 결과를 `SHOW CREATE TABLE`로 그대로 추출한 것입니다(추측 아님).

---

## 1. 반드시 적용해야 함 — 이번 세션에서 신규로 추가된 테이블 (챗봇 대화 로그)

회원별 챗봇 질문/답변을 영구 기록해 관리자가 모니터링할 수 있도록 `chat_logs` / `chat_log_recipe_ids` 테이블을 신규로 추가했습니다.

```sql
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
```

**관련 코드**: `domain/chatbot/entity/ChatLog.java`, `ChatLogRepository`, `ChatLogService`, `AdminChatController` (`GET /admin/chats`).

> (변경 이력) 최초 설계는 프론트가 발급하는 `sessionId`를 별도 컬럼(`session_id`)으로 저장했으나, 검토 끝에 `sessionId` 개념 자체를 제거하고 `memberId` + Redis 30분 TTL만으로 대화 맥락을 관리하는 방식으로 되돌렸습니다. 이에 따라 `chat_logs.session_id` 컬럼도 함께 제거했습니다. 위 DDL은 이 변경이 반영된 최종본입니다.

---

## 2. 확인 필요 — 이번 세션이 만든 게 아니라, 로컬 dev DB 검증 중 우연히 발견된 기존 drift

DDL을 검증하려고 로컬 dev DB에 `ddl-auto=update`를 실행했더니, **제가 이번 세션에서 건드리지 않은 `Member` 엔티티 관련 컬럼들도 함께 자동 반영**됐습니다. 즉 로컬 dev DB의 실제 스키마가 이미 배포된 엔티티 코드(이 세션 이전부터 존재하던 `email`, `termsAgreed` 필드)보다 뒤처져 있었다는 뜻입니다.

```sql
ALTER TABLE members ADD COLUMN email varchar(255);
ALTER TABLE members MODIFY COLUMN oauth_id varchar(255);
ALTER TABLE members ADD COLUMN terms_agreed bit NOT NULL;
ALTER TABLE members DROP INDEX uk_member_email;
ALTER TABLE members ADD CONSTRAINT uk_member_email UNIQUE (email);
```

**⚠️ 운영 DB에 그대로 실행하면 안 될 수 있습니다.** `terms_agreed bit NOT NULL`을 DEFAULT 없이 추가하는데, 운영 `members` 테이블에 이미 회원 데이터가 있다면 이 ALTER가 실패하거나(strict mode) 의도와 다르게 동작할 수 있습니다. 운영에 적용할 때는 아래처럼 기본값을 명시하는 걸 권장합니다(엔티티 기본값 `false`와 일치):

```sql
ALTER TABLE members ADD COLUMN email varchar(255);
ALTER TABLE members ADD COLUMN terms_agreed bit(1) NOT NULL DEFAULT 0;
-- oauth_id 컬럼 정의/유니크 제약 변경은 운영에 이미 email 컬럼과 관련 제약이 있는지 먼저 확인 후 필요한 것만 적용
```

**다음 확인이 필요합니다** — 이건 제가 직접 확인할 수 없으니 운영 DB 담당자가 `SHOW CREATE TABLE members;`로 직접 봐주셔야 합니다:
- 운영 `members` 테이블에 이미 `email`, `terms_agreed` 컬럼이 있는지 (이미 있다면 이 섹션은 무시하면 됨 — 로컬 dev DB만 뒤처져 있었던 것일 수 있음)
- 없다면, 언제부터 이 필드들이 코드에 있었는지(이번 세션과 무관하게 이전부터 있었음. 그런데도 운영에 반영이 안 됐다면 별도로 짚고 넘어가야 할 배포 프로세스 문제일 수 있습니다)

---

## 3. 조치 불필요 — 임베딩 관련

이번 세션에서 챗봇의 벡터 임베딩 검색 기능 전체(`EmbeddingClient`, `RecipeSearchService`, `RecipeEmbeddingService`, `VectorMathUtil`, 관련 API)를 삭제했지만, **`recipes.embedding` 컬럼 자체는 DB에서 지우지 않았습니다.** Java 엔티리 매핑(`Recipe.embedding` 필드)만 제거했습니다.

- `ddl-auto=validate`는 엔티티에 매핑된 컬럼이 DB에 있는지만 검사하고, **엔티티에 없는데 DB에만 남아있는 컬럼은 문제 삼지 않습니다.** 따라서 운영 배포 시 아무 조치 없이도 정상 기동합니다.
- 즉 운영 DB의 `recipes.embedding` 컬럼은 이제 아무도 쓰지 않는 죽은 컬럼으로 남게 되지만, 당장 지울 필요는 없습니다. 나중에 정리하고 싶다면 별도로 `ALTER TABLE recipes DROP COLUMN embedding;`을 실행하면 되는데, 이건 되돌리기 어려운 작업이니 원하실 때 별도 요청해주세요(이번엔 하지 않았습니다).

---

## 배포 순서 요약

1. **1번 DDL(`chat_logs`, `chat_log_recipe_ids`)을 운영 DB에 실행** (필수)
2. **2번은 운영 DB 실제 상태를 먼저 확인**한 후, 필요한 경우에만 (DEFAULT 값 포함해서) 적용
3. 위 DDL 적용 완료 후 애플리케이션 배포 (순서가 바뀌면 `ddl-auto=validate`가 기동을 막습니다)
4. 3번(임베딩 컬럼)은 조치 불필요, 참고만
