package com.github.forax.carrier.java.lang.runtime;

import com.github.forax.carrier.java.lang.runtime.Matcher.CarrierMetadata;
import com.github.forax.carrier.java.lang.runtime.Pattern.RecordPattern;
import com.github.forax.carrier.java.lang.runtime.Pattern.TypePattern;

import java.lang.invoke.MethodHandles;

import static com.github.forax.carrier.java.lang.runtime.Matcher.of;
import static com.github.forax.carrier.java.lang.runtime.Matcher.throwNPE;
import static java.lang.invoke.MethodType.methodType;

public class AssignmentExamples {
  public static void main(String[] args) throws Throwable {
    record MinMax(int min, int max) {}

    int v1;
    int v2;
    // MinMax(v1, v2) = v1 < v2? new MinMax(v1, v2): new MinMax(v2, v1);

    var lookup = MethodHandles.lookup();

    var carrierType = methodType(Object.class, int.class, int.class);
    var carrierMetadata = CarrierMetadata.fromCarrier(carrierType);
    var empty = carrierMetadata.empty();

    var pattern = new RecordPattern(MinMax.class,
        new TypePattern(int.class),
        new TypePattern(int.class));
    var matcher = pattern.toMatcher(lookup, MinMax.class, carrierType, 0, throwNPE(MinMax.class, "NPE !"));
    var op = of(empty, matcher);

    // MinMax(v1, v2) = new MinMax(v1, v2):
    v1 = 3;
    v2 = 4;
    var minmax1 = v1 < v2? new MinMax(v1, v2): new MinMax(v2, v1);
    var carrier1 = op.invokeExact(minmax1);
    v1 = (int) carrierMetadata.accessor(0).invokeExact(carrier1);
    v2 = (int) carrierMetadata.accessor(1).invokeExact(carrier1);
    System.out.println("min1 " + v1);
    System.out.println("max1 " + v2);

    // MinMax(v1, v2) = new MinMax(v2, v1):
    v1 = 4;
    v2 = 3;
    var minmax2 = v1 < v2? new MinMax(v1, v2): new MinMax(v2, v1);
    var carrier2 = op.invokeExact(minmax1);
    v1 = (int) carrierMetadata.accessor(0).invokeExact(carrier2);
    v2 = (int) carrierMetadata.accessor(1).invokeExact(carrier2);
    System.out.println("min2 " + v1);
    System.out.println("max2 " + v2);
  }
}
