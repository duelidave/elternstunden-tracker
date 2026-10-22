package de.elternstunden.family.domain;

/** Outcome of matching child data against the children (7.1.4 step 5). */
public enum MatchResult {
    /** Exactly one family has a child with these data. */
    MATCHED,
    NO_MATCH,
    /** Children with these data exist in several families. */
    AMBIGUOUS,
    /** Too many claims for this child within 24 hours; no lookup was made (9.1). */
    THROTTLED
}
