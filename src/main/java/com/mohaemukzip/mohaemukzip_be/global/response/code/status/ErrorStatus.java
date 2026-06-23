package com.mohaemukzip.mohaemukzip_be.global.response.code.status;

import com.mohaemukzip.mohaemukzip_be.global.response.code.BaseCode;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum ErrorStatus implements BaseCode {
    // Common Error
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "COMMON500", "서버 에러입니다. 관리자에게 문의하세요."),
    BAD_REQUEST(HttpStatus.BAD_REQUEST, "COMMON400", "잘못된 요청입니다."),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "COMMON401", "인증이 필요합니다."),
    FORBIDDEN(HttpStatus.FORBIDDEN, "COMMON403", "금지된 요청입니다."),
    NOT_FOUND(HttpStatus.NOT_FOUND, "COMMON404", "찾을 수 없는 요청입니다."),

    // 인증관련 에러
    TOKEN_MISSING(HttpStatus.UNAUTHORIZED, "AUTH4001", "Access Token 또는 Refresh Token이 누락되었습니다."),
    REFRESH_TOKEN_NOT_FOUND(HttpStatus.UNAUTHORIZED, "AUTH4002", "로그인 정보가 만료되었습니다."),
    REFRESH_TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED, "AUTH4003", "Refresh Token이 만료되었습니다."),
    INVALID_TOKEN(HttpStatus.UNAUTHORIZED, "AUTH4004", "유효하지 않은 토큰입니다."),
    REFRESH_TOKEN_MISMATCH(HttpStatus.FORBIDDEN, "AUTH4005", "Refresh Token이 일치하지 않습니다."),
    DUPLICATE_LOGIN_ID(HttpStatus.BAD_REQUEST, "AUTH4006", "중복된 로그인 아이디입니다."),
    INVALID_PASSWORD(HttpStatus.BAD_REQUEST, "AUTH4007", "비밀번호가 올바르지 않습니다."),
    MALFORMED_TOKEN(HttpStatus.UNAUTHORIZED, "AUTH4008", "토큰의 형식이 올바르지 않습니다."),
    INVALID_SIGNATURE(HttpStatus.UNAUTHORIZED, "AUTH4009", "토큰의 서명이 올바르지 않습니다."),
    UNSUPPORTED_TOKEN(HttpStatus.UNAUTHORIZED, "AUTH4010", "지원하지 않는 토큰 유형입니다."),
    ILLEGAL_ARGUMENT_TOKEN(HttpStatus.UNAUTHORIZED, "AUTH4011", "토큰의 인수가 올바르지 않습니다."),
    TOKEN_PARSING_ERROR(HttpStatus.UNAUTHORIZED, "AUTH4012", "토큰 파싱 중 오류가 발생했습니다."),
    // 카카오 로그인 관련 에러
    KAKAO_API_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "AUTH5001", "카카오 API 호출 중 오류가 발생했습니다."),
    INVALID_KAKAO_TOKEN(HttpStatus.UNAUTHORIZED, "AUTH4013", "유효하지 않은 카카오 액세스 토큰입니다."),
    // 애플 로그인 관련 에러
    APPLE_PUBLIC_KEY_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "AUTH5002", "애플 공개키 조회에 실패했습니다."),
    INVALID_APPLE_TOKEN(HttpStatus.UNAUTHORIZED, "AUTH4014", "유효하지 않은 애플 토큰입니다."),

    // 멤버 관련 에러
    MEMBER_NOT_FOUND(HttpStatus.BAD_REQUEST, "MEMBER4001", "사용자가 없습니다."),
    ALREADY_WITHDRAWN_MEMBER(HttpStatus.BAD_REQUEST, "MEMBER4002", "이미 탈퇴한 회원입니다."),
    INVALID_PROFILE_IMAGE_KEY(HttpStatus.BAD_REQUEST, "MEMBER4003", "유효하지 않은 프로필 이미지 키입니다."),
    MEMBER_FORBIDDEN(HttpStatus.FORBIDDEN, "MEMBER403", "해당 리소스에 접근할 권한이 없습니다."),
    // 서비스 약관 관련 에러
    TERM_NOT_FOUND(HttpStatus.NOT_FOUND, "T001", "약관을 찾을 수 없습니다."),
    REQUIRED_TERM_NOT_AGREED(HttpStatus.BAD_REQUEST, "T002", "필수 약관에 동의하지 않았습니다."),
    DUPLICATE_TERM_ID(HttpStatus.BAD_REQUEST, "T003", "중복된 약관 ID입니다."),
    
    //재료 관련 에러
    INGREDIENT_NOT_FOUND(HttpStatus.NOT_FOUND, "INGREDIENT4001", "해당 재료를 찾을 수 없습니다."),
    ALREADY_FAVORITE(HttpStatus.CONFLICT, "FAVORITE4091", "이미 즐겨찾기에 등록된 재료입니다."),
    FAVORITE_NOT_FOUND(HttpStatus.NOT_FOUND, "FAVORITE4001", "해당 즐겨찾기 재료를 찾을 수 없습니다."),
    SEARCH_NOT_FOUND(HttpStatus.NOT_FOUND, "SEARCH4001", "존재하지 않는 검색어입니다."),
    MEMBER_NOT_MATCH(HttpStatus.FORBIDDEN, "MEMBER4004", "해당 검색어를 삭제할 권한이 없습니다."),
    INGREDIENT_ALREADY_REQUESTED(HttpStatus.BAD_REQUEST, "INGREDIENT4009", "이미 요청한 재료입니다."),
    MEMBER_INGREDIENT_NOT_FOUND(HttpStatus.NOT_FOUND, "INGREDIENT404", "해당 재료를 찾을 수 없습니다."),



    //점수 관련 에러
    INVALID_SCORE(HttpStatus.BAD_REQUEST, "SCORE4001", "유효하지 않은 점수입니다."),
    INVALID_LEVEL(HttpStatus.BAD_REQUEST, "SCORE4002", "유효하지 않은 레벨입니다."),
    INVALID_RATING(HttpStatus.BAD_REQUEST, "SCORE4003", "유효하지 않은 평점입니다."),

    // 이미지 관련 에러
    INVALID_IMAGE_EXTENSION(HttpStatus.BAD_REQUEST, "IMAGE4001", "유효하지 않은 이미지 확장자입니다."),
    UNSUPPORTED_IMAGE_FORMAT(HttpStatus.BAD_REQUEST, "IMAGE4002", "지원하지 않는 이미지 형식입니다."),

    // 카테고리 관련 에러
    CATEGORY_NOT_FOUND(HttpStatus.NOT_FOUND, "CATEGORY4001", "해당 카테고리를 찾을 수 없습니다."),

    // 요리(Dish) 관련 에러
    DISH_NOT_FOUND(HttpStatus.NOT_FOUND, "DISH4001", "해당 요리를 찾을 수 없습니다."),

    // 레시피 관련 에러
    RECIPE_NOT_FOUND(HttpStatus.NOT_FOUND, "RECIPE4001", "해당 레시피를 찾을 수 없습니다."),
    RECIPE_ALREADY_EXISTS(HttpStatus.BAD_REQUEST, "RECIPE4002", "이미 저장된 레시피입니다."),
    INVALID_RATING_VALUE(HttpStatus.BAD_REQUEST, "RECIPE4003",   "난이도는 1부터 5 사이의 값입니다."),
    TRANSCRIPT_NOT_AVAILABLE(HttpStatus.BAD_REQUEST, "RECIPE4004", "해당 영상에 자막이 없어 요약을 생성할 수 없습니다."),


    // Gemini 관련 에러
    GEMINI_BAD_REQUEST(HttpStatus.BAD_REQUEST, "GEMINI4001", "잘못된 Gemini 요청입니다."),
    GEMINI_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "GEMINI5001", "Gemini 서버 오류입니다."),
    EXTERNAL_API_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "EXTERNAL5001", "외부 API 호출 중 오류가 발생했습니다."),
    SUMMARY_CREATION_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "GEMINI5002", "요약문 생성에 실패했습니다."),
    // 미션 관련 에러
    NO_AVAILABLE_MISSION(HttpStatus.NOT_FOUND, "MISSION4001", "퀘스트로 할당할 미션이 남아있지 않습니다.")
    ;
    private final HttpStatus httpStatus;
    private final String code;
    private final String message;

}
