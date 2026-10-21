package de.elternstunden.common.mail;

/** A transactional mail with a plain-text and an HTML part (7.11). */
public record MailMessage(String to, String subject, String text, String html) {}
