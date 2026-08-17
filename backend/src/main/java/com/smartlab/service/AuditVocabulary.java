package com.smartlab.service;

public final class AuditVocabulary {
    public static final String ROLE_CREATED = "ROLE_CREATED";
    public static final String ROLE_UPDATED = "ROLE_UPDATED";
    public static final String ROLE_PERMISSIONS_UPDATED = "ROLE_PERMISSIONS_UPDATED";
    public static final String PERMISSION_CREATED = "PERMISSION_CREATED";
    public static final String PERMISSION_UPDATED = "PERMISSION_UPDATED";
    public static final String USER_ROLES_UPDATED = "USER_ROLES_UPDATED";
    public static final String USER_PERMISSION_OVERRIDE_SET = "USER_PERMISSION_OVERRIDE_SET";
    public static final String USER_PERMISSION_OVERRIDE_REMOVED = "USER_PERMISSION_OVERRIDE_REMOVED";
    public static final String POST_REVIEWED = "POST_REVIEWED";
    public static final String PROJECT_MEMBER_CREATED = "PROJECT_MEMBER_CREATED";
    public static final String PROJECT_MEMBER_UPDATED = "PROJECT_MEMBER_UPDATED";
    public static final String PROJECT_JOIN_REQUEST_CREATED = "PROJECT_JOIN_REQUEST_CREATED";
    public static final String PROJECT_JOIN_REQUEST_UPDATED = "PROJECT_JOIN_REQUEST_UPDATED";
    public static final String ROLE = "ROLE";
    public static final String PERMISSION = "PERMISSION";
    public static final String USER = "USER";
    public static final String POST = "POST";
    public static final String PROJECT_MEMBER = "PROJECT_MEMBER";
    public static final String PROJECT_JOIN_REQUEST = "PROJECT_JOIN_REQUEST";

    private AuditVocabulary() {
    }
}
