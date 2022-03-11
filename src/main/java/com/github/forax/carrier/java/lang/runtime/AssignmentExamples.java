package com.github.forax.carrier.java.lang.runtime;

import com.github.forax.carrier.java.lang.runtime.Matcher.CarrierMetadata;

import java.lang.invoke.MethodHandles;

import static com.github.forax.carrier.java.lang.runtime.Matcher.bind;
import static com.github.forax.carrier.java.lang.runtime.Matcher.cast;
import static com.github.forax.carrier.java.lang.runtime.Matcher.doNotMatch;
import static com.github.forax.carrier.java.lang.runtime.Matcher.index;
import static com.github.forax.carrier.java.lang.runtime.Matcher.instanceOfResult;
import static com.github.forax.carrier.java.lang.runtime.Matcher.isInstance;
import static com.github.forax.carrier.java.lang.runtime.Matcher.isNull;
import static com.github.forax.carrier.java.lang.runtime.Matcher.of;
import static com.github.forax.carrier.java.lang.runtime.Matcher.or;
import static com.github.forax.carrier.java.lang.runtime.Matcher.project;
import static com.github.forax.carrier.java.lang.runtime.Matcher.test;
import static java.lang.invoke.MethodType.methodType;

public class AssignmentExamples {
  public static void main(String[] args) throws Throwable {
    record MinMax(int min, int max) {}

    int v1;
    int v2;
    // MinMax(v1, v2) = v1 < v2? new MinMax(v1, v2): new MinMax(v2, v1);

    var lookup = MethodHandles.lookup();

    var carrierMetadata = CarrierMetadata.fromCarrier(methodType(Object.class, int.class, int.class));
    var empty = carrierMetadata.empty();

    var minMaxMetadata = CarrierMetadata.fromRecord(lookup, MinMax.class);

    var op = of(empty,
        or(
          project(minMaxMetadata.accessor(0),
              bind(0, carrierMetadata)),
          project(minMaxMetadata.accessor(1),
              bind(1, carrierMetadata))
        )
    );

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
