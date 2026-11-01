package com.fintechassist.chat;

/** Authenticated caller, extracted from the JWT. The raw token is kept so tools can call banking-service as the user. */
public record JwtUser(long userId, String name, String token) {}
