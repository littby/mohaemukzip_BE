# 회원 탈퇴 수정: 로컬 확인과 운영 배포

이슈: [#321](https://github.com/mohaemukzip/mohaemukzip_BE/issues/321)

**로컬 확인 후 dev PR 검토는 가능합니다. main 병합 전에는 운영 DB 변경과 검증이 필요합니다.** main에 병합하면 현재 CI/CD가 EC2 배포를 시작합니다.

## 무엇을 고쳤나요?

| 문제 | 변경 후 |
|---|---|
| 없어진 `member_cook_histories`에 DELETE 실행 | 해당 호출 제거. 현재 `cooking_records` 삭제는 유지 |
| 챗봇 기록이 회원을 참조해 회원 삭제가 막힘 | 같은 DB 트랜잭션에서 회원 연결을 해제한 뒤 회원 삭제 |
| 연결 없는 기록을 관리자 조회하면 500 | `memberId: null`로 반환 |
| 비동기 로그 저장이 탈퇴와 겹칠 수 있음 | 같은 회원 잠금 사용. 이미 삭제·비활성화된 회원의 새 로그는 저장하지 않음 |
| 회원 삭제가 실패했는데 토큰은 먼저 삭제됨 | DB 커밋 후 Refresh Token과 Redis 대화 캐시 정리 |

질문·답변 원문, 생성 시각, 추천 레시피 ID는 보관합니다. 일반 앱의 탈퇴 API 응답 형식은 그대로입니다. 관리자 조회 응답의 `memberId`는 탈퇴 기록에서 null이 됩니다.

## 1. 외부 API 호출 없이 로컬 회귀 테스트

브랜치: `fix/321-member-withdrawal-chat-log-retention`

```bash
./gradlew test --tests '*MemberWithdrawalIntegrationTest' --tests '*ChatLogServiceTest'
```

새 통합 테스트는 운영·개인 dev 설정을 읽지 않는 H2 메모리 DB와 Redis 대역을 사용합니다. 실제 외부 AI·메일·운영 DB 요청은 하지 않습니다.

확인하는 내용:

- 옛 테이블 없이 회원 탈퇴 성공.
- 대화와 추천 ID 보관, 다른 회원 데이터 유지.
- 연결 없는 기록의 관리자 조회 성공.
- DB 삭제 실패·외부 트랜잭션 롤백 시 회원과 대화 연결 유지, Redis 미변경.
- 커밋 후 Redis 장애 처리.
- 로그 저장 먼저 / 탈퇴 먼저인 두 동시 실행 순서.
- 삭제·비활성 회원의 늦은 로그 저장 생략, 저장·커밋 실패 처리.

H2는 운영 MySQL의 DDL·잠금 동작을 전부 검증하지 않습니다. 아래 로컬 MySQL 확인도 필요합니다.

## 2. 로컬 MySQL과 Swagger에서 직접 확인

로컬 DB 주소·DB명을 먼저 확인합니다. 아래 SQL은 선택한 DB의 접속 콘솔에서 실행합니다.

```sql
SELECT DATABASE();

SELECT COLUMN_TYPE, IS_NULLABLE
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME = 'chat_logs'
  AND COLUMN_NAME = 'member_id';
```

`IS_NULLABLE`은 `YES`여야 합니다. 기존 로컬 DB가 `NO`이면 엔티티 설정만 바꿔서 해결됐다고 가정하지 말고, 아래 운영 사전 변경 항목과 같은 방식으로 **로컬 스키마**를 맞춥니다.

1. 로컬 Swagger에서 별도 테스트 회원으로 로그인합니다. 관리자 계정을 탈퇴시키지 않습니다.
2. 해당 계정에 기존 로컬 대화 기록이 있으면 기록 ID와 추천 ID를 확인합니다. 없으면 위 회귀 테스트가 보관 시나리오를 자동으로 생성하므로, 검증을 위해 추가 AI 요청을 할 필요는 없습니다.
3. `DELETE /auth/withdrawal`을 요청하고 성공 응답을 확인합니다.
4. `members`에서 테스트 회원이 없어지고, 기존 `chat_logs`는 남으면서 `member_id`만 null이 되었는지 확인합니다. `chat_log_recipe_ids`도 남아 있어야 합니다.
5. **다른 관리자 계정**으로 로그인해 `GET /admin/chats` 전체 조회가 성공하는지 확인합니다. 탈퇴한 계정으로 요청하면 인증 실패가 정상입니다.
6. 다른 회원의 대화와 회원 연결이 유지되는지 확인합니다.

정상 로그는 외래키 연결 해제 후에도 전체 관리자 조회에 포함됩니다. 이전 회원번호로 필터하면 해당 기록은 조회되지 않습니다.

## 3. main 병합 전 운영 DB 확인

현재 운영 스키마는 아직 직접 조회하지 않았습니다. 다음은 조회만 수행합니다.

```sql
SELECT DATABASE(), VERSION();

SELECT TABLE_NAME
FROM information_schema.TABLES
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME IN ('members', 'member_cook_histories', 'cooking_records',
                     'chat_logs', 'chat_log_recipe_ids');

SHOW CREATE TABLE chat_logs;
SHOW CREATE TABLE chat_log_recipe_ids;
```

확인할 내용:

| 항목 | 필요한 상태 |
|---|---|
| `member_cook_histories` | 현재 사용하지 않음. 없는 상태에서도 새 코드는 동작 |
| `cooking_records` | 현재 조리 기록 테이블 존재 |
| `chat_logs.member_id` | NULL 허용 |
| `chat_logs` → `members` 외래키 | 유지. 새 코드는 연결을 먼저 해제 |
| `chat_log_recipe_ids` → `chat_logs` 외래키 | 유지. 보관 대상인 대화·추천 ID를 삭제하지 않음 |

운영에 옛 테이블이 남아 있고 실제 데이터가 있다면, 기존 데이터·참조 정리는 별도로 확인합니다. 이번 배포 과정에서 임의로 DROP하지 않습니다.

## 4. 필요한 운영 사전 변경

**새 테이블은 필요 없지만 `chat_logs.member_id`가 NOT NULL이면 변경이 필요합니다.**

다음은 기존 마이그레이션 문서처럼 컬럼이 signed `BIGINT NOT NULL`인 경우의 예시입니다. 실제 `SHOW CREATE TABLE`에서 타입·속성을 확인하고 기존 속성을 보존한 SQL로 확정해야 합니다.

```sql
-- 운영 적용 예시. 실제 컬럼 정의 확인 및 DB 변경 검토 후에만 실행.
ALTER TABLE chat_logs MODIFY COLUMN member_id BIGINT NULL;
```

외래키와 인덱스를 제거하거나 `FOREIGN_KEY_CHECKS`를 끄지 않습니다. 코드 배포가 DB 변경까지 자동으로 안전하게 완료한다고 가정하지 않습니다. `ddl-auto=update`에 맡기지 말고 변경 후 `IS_NULLABLE=YES`를 확인합니다. 이 SQL은 애플리케이션 시작 시 자동 실행되지 않습니다.

권장 진행 순서:

1. 로컬 회귀 테스트 및 로컬 MySQL 수동 검증.
2. 작업 브랜치 → dev PR 검토·병합.
3. 운영 스키마 조회, DB 변경 영향 확인·복구 준비, NULL 허용 변경 및 재확인.
4. dev → main PR 검토. 현재 CI는 테스트를 건너뛰므로 수동 검증 결과를 첨부.
5. main 병합으로 배포한 뒤 서버 기동·탈퇴·관리자 조회를 확인. 운영 검증 계정 사용은 별도로 정함.

기존 코드와 스키마는 NULL 기록을 처리하지 못합니다. 새 코드가 연결 없는 기록을 만든 뒤에는 예전 코드를 단순 재배포하거나 컬럼을 NOT NULL로 되돌리는 것을 복구 방법으로 가정하면 안 됩니다. 대화 연결 해제와 회원 삭제는 기존 회원 식별로 자동 복원할 수 없는 변경입니다.

## 보관 및 운영상 한계

- 회원 연결 해제는 완전한 익명화가 아닙니다. 원문에 이름·연락처 등 식별 정보가 포함될 수 있습니다. 분석용 원문의 정제·접근 권한·보관 기간은 별도 정책이 필요합니다.
- 현재 로그로 추천 노출 횟수를 집계할 수 있습니다. 실제 클릭·조리 완료 횟수와는 다른 지표입니다. 전체 기록 수에는 `COUNT(*)`를 사용하며, `COUNT(member_id)`나 회원 INNER JOIN은 연결 없는 기록을 제외합니다.
- DB 커밋 후 Redis 장애는 완료된 회원 삭제를 500으로 바꾸지 않고 오류 로그로 남깁니다. 이번 범위에는 영속 재시도 큐를 추가하지 않았습니다. Refresh Token은 기존 7일 TTL, 대화 캐시는 마지막 활동 후 30분 TTL을 가지며, 남은 토큰으로도 삭제된 회원의 인증·재발급은 회원 조회에서 차단됩니다. 장애 후 잔여 키 정리는 운영 확인 대상입니다.
- 비동기 로그 저장과 탈퇴의 DB 작업은 테스트했지만, 운영 MySQL 동시 실행과 실제 Redis 연결은 아직 검증하지 않았습니다.
- 실제 10월 11일 운영 장애 원인은 받은 로그에서 확정하지 못했습니다. 이번 수정은 코드에서 재현한 결함을 해결합니다.
