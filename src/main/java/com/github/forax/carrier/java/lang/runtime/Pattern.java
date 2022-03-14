package com.github.forax.carrier.java.lang.runtime;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.invoke.MethodType;
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

  default MethodHandle toMatcher(Lookup lookup, Class<?> receiverType, MethodType carrierType, int firstBinding, boolean emitNPE) {
    var carrierMetadata = CarrierMetadata.fromCarrier(carrierType);
    return toMatcher(lookup, receiverType, new BindingAllocator(carrierMetadata, firstBinding), emitNPE? this: null);
  }

  class BindingAllocator {
    private final CarrierMetadata carrierMetadata;
    private int binding;

    public BindingAllocator(CarrierMetadata carrierMetadata, int binding) {
      this.carrierMetadata = carrierMetadata;
      this.binding = binding;
    }

    public CarrierMetadata carrierMetadata() {
      return carrierMetadata;
    }

    public int nextBinding() {
      return binding++;
    }
  }

  default MethodHandle toMatcher(Lookup lookup, Class<?> receiverType, BindingAllocator bindingAllocator, Pattern rootPattern) {
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
        yield test(isInstance(receiverType, typePattern.type),
            cast(receiverType, bind(bindingAllocator.nextBinding(), bindingAllocator.carrierMetadata)),
            doNotMatch(receiverType));
      }
      //case GuardPattern guardPattern ->
      //    and(
      //        guardPattern.pattern.toMatcher(lookup, receiverType, bindingAllocator, fail),
      //        test(guardPattern.guard, doMatch(receiverType), doNotMatch(receiverType)));
      case RecordPattern recordPattern -> {
        var recordClass = recordPattern.recordClass;
        var carrierMetadata = CarrierMetadata.fromRecord(lookup, recordClass);
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
                      rootPattern));
            })
            .toArray(MethodHandle[]::new);

        var matcher = (MethodHandle) null;
        for(var i = matchers.length; --i >= 0;) {
          var m = matchers[i];
          matcher = matcher == null? m: and(m, matcher);
        }
        if (matcher == null) { // empty record
          matcher = doMatch(recordClass);
        }
        var result = (receiverType == recordClass)?
            matcher:
            test(isInstance(receiverType, recordClass),
                cast(receiverType, matcher),
                doNotMatch(receiverType));
        yield test(isNull(receiverType),
            rootPattern == null? doNotMatch(receiverType): throwNPE(receiverType, rootPattern, this),
            result);
      }
      //case DeconstructorPattern deconstructorPattern -> {}
      case OrPattern orPattern ->
          or(
              orPattern.pattern1.toMatcher(lookup, receiverType, bindingAllocator, rootPattern),
              orPattern.pattern2.toMatcher(lookup, receiverType, bindingAllocator, rootPattern));
      case ResultPattern resultPattern ->
          and(
              resultPattern.pattern.toMatcher(lookup, receiverType, bindingAllocator, rootPattern),
              index(receiverType, bindingAllocator.carrierMetadata, resultPattern.index));
    };
  }

  default String prefixErrorMessage(Pattern pattern) {
    return switch (this) {
      case NullPattern nullPattern -> "null";
      case ConstantPattern constantPattern -> "" + constantPattern.constant;
      case TypePattern typePattern -> {
        if (typePattern == pattern) {
          yield shortName(typePattern.type) + "^";
        }
        yield shortName(typePattern.type);
      }
      case RecordPattern recordPattern -> {
        if (recordPattern == pattern) {
          yield shortName(recordPattern.recordClass) + "^";
        }
        var builder = new StringBuilder()
            .append(shortName(recordPattern.recordClass))
            .append('(');
        var separator = "";
        for(var subPattern: recordPattern.patterns) {
          var errorMessage = subPattern.prefixErrorMessage(pattern);
          builder.append(separator).append(errorMessage);
          if (errorMessage.endsWith("^")) {
            yield builder.toString();
          }
          separator = ", ";
        }
        yield builder.append(')').toString();
      }
      case OrPattern orPattern -> {
        var errorMessage = orPattern.pattern1.prefixErrorMessage(pattern);
        if (errorMessage.endsWith("^")) {
          yield errorMessage;
        }
        yield orPattern.pattern2.prefixErrorMessage(pattern);
      }
      case ResultPattern resultPattern -> resultPattern.pattern.prefixErrorMessage(pattern);
    };
  }

  private static String shortName(Class<?> type) {
    if (type.isArray()) {
      return shortName(type.getComponentType()) + "[]";
    }
    var name = type.getName();
    var index = name.lastIndexOf('.');
    return index == -1? name: name.substring(index + 1);
  }
}
