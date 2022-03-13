package com.github.forax.carrier.java.lang.runtime;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.invoke.MethodType;
import java.util.Arrays;
import java.util.stream.IntStream;

import static com.github.forax.carrier.java.lang.runtime.Matcher.*;

public sealed interface Pattern {
  record NullPattern() implements Pattern {}
  record ConstantPattern(Object constant) implements Pattern {}
  record TypePattern(Class<?> type) implements Pattern {}
  //record GuardPattern(Pattern pattern, MethodHandle guard) implements Pattern {}
  record RecordPattern(Class<?> recordClass, Pattern... patterns) implements Pattern {}
  //record DeconstructorPattern(MethodHandle deconstructor, MethodType carrierType, Pattern... patterns) implements Pattern {}

  record OrPattern(Pattern pattern1, Pattern pattern2) implements Pattern {}
  record ResultPattern(int index, Pattern pattern) implements Pattern {}

  default MethodHandle toMatcher(Lookup lookup, Class<?> receiverType, MethodType carrierType, int firstBinding, MethodHandle nullMatcher) {
    var carrierMetadata = CarrierMetadata.fromCarrier(carrierType);
    return toMatcher(lookup, receiverType, new BindingAllocator(carrierMetadata, firstBinding), nullMatcher);
  }

  /*private*/ class BindingAllocator {
    private final CarrierMetadata carrierMetadata;
    private int binding;

    public BindingAllocator(CarrierMetadata carrierMetadata, int binding) {
      this.carrierMetadata = carrierMetadata;
      this.binding = binding;
    }

    public int nextBinding() {
      return binding++;
    }
  }

  private MethodHandle toMatcher(Lookup lookup, Class<?> receiverType, BindingAllocator bindingAllocator, MethodHandle nullMatcher) {
    return switch (this) {
      case NullPattern nullPattern ->
          test(isNull(receiverType),
              doMatch(receiverType),
              doNotMatch(receiverType));
      case ConstantPattern constantPattern ->
          test(isEquals(receiverType, constantPattern.constant),
              doMatch(receiverType),
              doNotMatch(receiverType));
      case TypePattern typePattern -> {
        if (receiverType == typePattern.type) {
          yield bind(bindingAllocator.nextBinding(), bindingAllocator.carrierMetadata);
        }
        yield test(isNull(receiverType),
            cast(receiverType, nullMatcher),
            test(isInstance(receiverType, typePattern.type),
                cast(receiverType, bind(bindingAllocator.nextBinding(), bindingAllocator.carrierMetadata)),
                doNotMatch(receiverType)));
      }
      //case GuardPattern guardPattern ->
      //    and(
      //        guardPattern.pattern.toMatcher(lookup, receiverType, bindingAllocator, fail),
      //        test(guardPattern.guard, doMatch(receiverType), doNotMatch(receiverType)));
      case RecordPattern recordPattern -> {
        var carrierMetadata = CarrierMetadata.fromRecord(lookup, recordPattern.recordClass);
        var patterns = recordPattern.patterns;
        var matchers = IntStream.range(0, patterns.length)
            .mapToObj(i -> {
              var pattern = patterns[i];
              var accessor = carrierMetadata.accessor(i);
              var returnType = accessor.type().returnType();
              return Matcher.project(accessor,
                  pattern.toMatcher(
                      lookup,
                      returnType,
                      bindingAllocator,
                      nullMatcher));
            })
            .toArray(MethodHandle[]::new);

        var matcher = (MethodHandle) null;
        for(var i = matchers.length; --i >= 0;) {
          var m = matchers[i];
          matcher = matcher == null? m: and(m, matcher);
        }
        assert matcher != null;

        if (receiverType == recordPattern.recordClass) {
          yield matcher;
        }
        yield test(isNull(receiverType),
            cast(receiverType, nullMatcher),
            test(isInstance(receiverType, recordPattern.recordClass),
                cast(receiverType, matcher),
                doNotMatch(receiverType)));
      }
      //case DeconstructorPattern deconstructorPattern -> {}
      case OrPattern orPattern ->
          or(
              orPattern.pattern1.toMatcher(lookup, receiverType, bindingAllocator, nullMatcher),
              orPattern.pattern2.toMatcher(lookup, receiverType, bindingAllocator, nullMatcher));
      case ResultPattern resultPattern ->
          and(
              resultPattern.pattern.toMatcher(lookup, receiverType, bindingAllocator, nullMatcher),
              index(receiverType, bindingAllocator.carrierMetadata, resultPattern.index));
    };
  }
}
