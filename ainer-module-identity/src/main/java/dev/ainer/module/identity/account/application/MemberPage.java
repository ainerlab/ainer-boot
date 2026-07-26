package dev.ainer.module.identity.account.application;

import java.util.List;

public record MemberPage(List<MemberSummary> members, int page, int size, int total) {
}
