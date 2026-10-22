package de.elternstunden.family.domain;

import java.time.LocalDate;

/** Child data as entered by a parent or an admin; names are trimmed. */
public record ChildData(String firstName, String lastName, LocalDate birthDate) {

    public ChildData {
        firstName = firstName.strip();
        lastName = lastName.strip();
    }
}
