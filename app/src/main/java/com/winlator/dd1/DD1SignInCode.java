package com.winlator.dd1;

// The code Steam is asking for, in the terms the screen needs to ask for it: from
// the authenticator app or from an email, which address it went to, and whether
// the last attempt was wrong. It carries no wording of its own so the state
// machine stays a plain object tests can drive.
public final class DD1SignInCode {
    public enum Source { EMAIL, AUTHENTICATOR }

    public final Source source;
    public final String emailHint;
    public final boolean previousWasWrong;

    private DD1SignInCode(Source source, String emailHint, boolean previousWasWrong) {
        this.source = source;
        this.emailHint = emailHint;
        this.previousWasWrong = previousWasWrong;
    }

    public static DD1SignInCode email(String address, boolean previousWasWrong) {
        return new DD1SignInCode(Source.EMAIL, address, previousWasWrong);
    }

    public static DD1SignInCode authenticator(boolean previousWasWrong) {
        return new DD1SignInCode(Source.AUTHENTICATOR, null, previousWasWrong);
    }
}
