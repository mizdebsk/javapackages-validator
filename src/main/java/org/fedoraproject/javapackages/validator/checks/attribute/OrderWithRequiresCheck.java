package org.fedoraproject.javapackages.validator.checks.attribute;

import org.fedoraproject.javapackages.validator.RpmAttributeCheck;
import org.fedoraproject.javapackages.validator.config.attribute.OrderWithRequiresConfig;

public class OrderWithRequiresCheck extends RpmAttributeCheck<OrderWithRequiresConfig> {
    public OrderWithRequiresCheck() {
        this(null);
    }

    public OrderWithRequiresCheck(OrderWithRequiresConfig config) {
        super(OrderWithRequiresConfig.class, config);
    }

    public static void main(String[] args) throws Exception {
        System.exit(new OrderWithRequiresCheck().executeCheck(args));
    }
}