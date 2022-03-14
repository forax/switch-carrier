package com.github.forax.carrier.java.lang.runtime;

import com.github.forax.carrier.java.lang.runtime.Matcher.CarrierMetadata;
import com.github.forax.carrier.java.lang.runtime.Pattern.OrPattern;
import com.github.forax.carrier.java.lang.runtime.Pattern.RecordPattern;
import com.github.forax.carrier.java.lang.runtime.Pattern.ResultPattern;
import com.github.forax.carrier.java.lang.runtime.Pattern.TypePattern;

import java.lang.invoke.MethodHandles;

import static com.github.forax.carrier.java.lang.runtime.Matcher.doNotMatch;
import static com.github.forax.carrier.java.lang.runtime.Matcher.of;
import static com.github.forax.carrier.java.lang.runtime.Matcher.switchResult;
import static java.lang.invoke.MethodType.methodType;

public class SwitchExamples {
  public static void main(String[] args) throws Throwable {
    record Point(int x, int y) {}
    record Rectangle(Point p1, Point p2) {}

    // Object o = ...
    // switch(o) {
    //   case Rectangle(Point p1, Point p2) -> ...
    //   case Object value -> ...
    // }

    var lookup = MethodHandles.lookup();

    var carrierType = methodType(Object.class, int.class, Point.class, Point.class, Object.class);
    var carrierMetadata = CarrierMetadata.fromCarrier(carrierType);
    var empty = carrierMetadata.empty();

    var pattern = new OrPattern(
      new ResultPattern(0,
          new RecordPattern(Rectangle.class,
              new TypePattern(Point.class),
              new TypePattern(Point.class)
          )
      ),
      new ResultPattern(1,
          new TypePattern(Object.class)
      )
    );

    var matcher = pattern.toMatcher(lookup, Object.class, carrierType, 1, false);
    var op = of(empty, matcher);

    // match: new Rectangle(new Point(1, 2), new Point(3, 4))
    var object1 = (Object) new Rectangle(new Point(1, 2), new Point(3, 4));
    var carrier1 = op.invokeExact(object1);
    System.out.println("carrier1: " + carrier1);
    System.out.println("result1: " + (int) switchResult(carrierMetadata).invokeExact(carrier1));
    System.out.println("binding 1 " + (Point) carrierMetadata.accessor(1).invokeExact(carrier1));
    System.out.println("binding 2 " + (Point) carrierMetadata.accessor(2).invokeExact(carrier1));

    // match: new Rectangle(null, new Point(1, 2))
    var object2 = (Object) new Rectangle(null, new Point(1, 2));
    var carrier2 = op.invokeExact(object2);
    System.out.println("carrier2: " + carrier2);
    System.out.println("result2: " + (int) switchResult(carrierMetadata).invokeExact(carrier2));

    // match: new Rectangle(new Point(1, 2), null)
    var object3 = (Object) new Rectangle(new Point(1, 2), null);
    var carrier3 = op.invokeExact(object3);
    System.out.println("carrier3: " + carrier3);
    System.out.println("result3: " + (int) switchResult(carrierMetadata).invokeExact(carrier3));

    // match: null
    var object4 = (Object) null;
    var carrier4 = op.invokeExact(object4);
    System.out.println("carrier4: " + carrier4);
    System.out.println("result4: " + (int) switchResult(carrierMetadata).invokeExact(carrier4));
    System.out.println("binding 3 " + (Object) carrierMetadata.accessor(3).invokeExact(carrier4));

    // match: "hello"
    var object5 = (Object) "hello";
    var carrier5 = op.invokeExact(object5);
    System.out.println("carrier5: " + carrier5);
    System.out.println("result5: " + (int) switchResult(carrierMetadata).invokeExact(carrier5));
    System.out.println("binding 3 " + (Object) carrierMetadata.accessor(3).invokeExact(carrier5));
  }
}
