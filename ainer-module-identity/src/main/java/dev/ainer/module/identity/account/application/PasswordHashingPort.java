package dev.ainer.module.identity.account.application;

public interface PasswordHashingPort {

    String hash(String rawPassword);
}
