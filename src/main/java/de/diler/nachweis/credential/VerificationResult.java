package de.diler.nachweis.credential;

import java.util.List;

public record VerificationResult(boolean valid, List<String> reasons) {
}
