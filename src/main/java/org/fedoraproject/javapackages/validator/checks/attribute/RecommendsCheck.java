package org.fedoraproject.javapackages.validator.checks.attribute;

import org.fedoraproject.javapackages.validator.RpmAttributeCheck;
import org.fedoraproject.javapackages.validator.config.attribute.RecommendsConfig;

public class RecommendsCheck extends RpmAttributeCheck<RecommendsConfig> {
    public RecommendsCheck() {
        super(RecommendsConfig.class);
    }

    public static void main(String[] args) throws Exception {
        System.exit(new RecommendsCheck().executeCheck(args));
    }
}
