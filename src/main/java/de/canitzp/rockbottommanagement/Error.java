package de.canitzp.rockbottommanagement;

import io.javalin.http.Context;

public class Error {

    // 1xx: Missing
    public static final int E_GENERIC_MISSING = 100;
    public static final int E_JSON_MISSING = 101;
    public static final int E_API_KEY_MISSING = 102;
    public static final int E_EMAIL_MISSING = 110;
    public static final int E_PASSWORD_MISSING = 111;
    public static final int E_USERNAME_MISSING = 112;
    public static final int E_PLAYER_DESIGN_MISSING = 113;
    public static final int E_ACCOUNT_UUID_MISSING = 114;
    public static final int E_OLD_PASSWORD_MISSING = 190;
    public static final int E_VERIFICATION_CODE_MISSING = 191;
    public static final int E_ACCOUNT_NOT_FOUND = 192;

    // 2xx: RegEx-Error
    public static final int E_GENERIC_REGEX = 200;
    public static final int E_EMAIL_REGEX = 210;
    public static final int E_PASSWORD_REGEX = 211;
    public static final int E_USERNAME_REGEX = 212;

    // 3xx: Already taken
    public static final int E_GENERIC_TAKEN = 300;
    public static final int E_EMAIL_TAKEN = 310;
    public static final int E_USERNAME_TAKEN = 312;

    // 4xx: Invalid values
    public static final int E_GENERIC_INVALID = 400;
    public static final int E_CREDENTIALS_INVALID = 401;
    public static final int E_API_KEY_INVALID = 402;
    public static final int E_PASSWORD_INVALID = 411;
    public static final int E_PLAYER_DESIGN_INVALID = 413;
    public static final int E_VERIFICATION_CODE_INVALID = 490;
    public static final int E_NOT_VERIFIED = 491;

    private int http_error_code = -1;
    private int internal_error_code = -1;

    private Error() {
    }

    public static Error create() {
        return new Error();
    }

    public Error codeInternal(int internalErrorCode) {
        this.internal_error_code = internalErrorCode;
        return this;
    }

    public Error codeHTTP(int httpErrorCode) {
        this.http_error_code = httpErrorCode;
        return this;
    }

    public void send(Context context) {
        if (this.http_error_code >= 100) {
            if (this.internal_error_code >= 0) {
                context.res.addIntHeader("RBM-Error", this.internal_error_code);
            }
            context.status(this.http_error_code);
        } else {
            context.status(500);
        }
    }
}
