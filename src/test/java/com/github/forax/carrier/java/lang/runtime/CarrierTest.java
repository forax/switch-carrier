package com.github.forax.carrier.java.lang.runtime;

import org.junit.jupiter.api.Test;

import java.lang.invoke.MethodType;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

public class CarrierTest {

  @Test
  public void carrierEmpty() throws Throwable {
    var methodType = MethodType.methodType(Object.class);
    var constructor = Carrier.constructor(methodType);
    var carrier = constructor.invokeExact();
    assertAll(
        () -> assertEquals(methodType, constructor.type()),
        () -> assertEquals(0, Carrier.components(methodType).length)
    );
  }

  @Test
  public void carrierI() throws Throwable {
    var methodType = MethodType.methodType(Object.class, int.class);
    var constructor = Carrier.constructor(methodType);
    var carrier = constructor.invokeExact(42);
    assertAll(
        () -> assertEquals(int.class, carrier.getClass().getDeclaredField("i0").getType()),
        () -> assertEquals(MethodType.methodType(int.class, Object.class), Carrier.component(methodType, 0).type()),
        () -> assertEquals(42, (int) Carrier.component(methodType, 0).invokeExact((Object) carrier)),
        () -> assertEquals(MethodType.methodType(int.class, Object.class), Carrier.components(methodType)[0].type()),
        () -> assertEquals(42, (int) Carrier.components(methodType)[0].invokeExact((Object) carrier))
    );
  }

  @Test
  public void carrierJ() throws Throwable {
    var methodType = MethodType.methodType(Object.class, long.class);
    var constructor = Carrier.constructor(methodType);
    var carrier = constructor.invokeExact(42L);
    assertAll(
        () -> assertEquals(long.class, carrier.getClass().getDeclaredField("l0").getType()),
        () -> assertEquals(MethodType.methodType(long.class, Object.class), Carrier.component(methodType, 0).type()),
        () -> assertEquals(42L, (long) Carrier.component(methodType, 0).invokeExact((Object) carrier)),
        () -> assertEquals(MethodType.methodType(long.class, Object.class), Carrier.components(methodType)[0].type()),
        () -> assertEquals(42L, (long) Carrier.components(methodType)[0].invokeExact((Object) carrier))
    );
  }

  @Test
  public void carrierF() throws Throwable {
    var methodType = MethodType.methodType(Object.class, float.class);
    var constructor = Carrier.constructor(methodType);
    var carrier = constructor.invokeExact(42f);
    assertAll(
        () -> assertEquals(int.class, carrier.getClass().getDeclaredField("i0").getType()),
        () -> assertEquals(MethodType.methodType(float.class, Object.class), Carrier.component(methodType, 0).type()),
        () -> assertEquals(42f, (float) Carrier.component(methodType, 0).invokeExact((Object) carrier)),
        () -> assertEquals(MethodType.methodType(float.class, Object.class), Carrier.components(methodType)[0].type()),
        () -> assertEquals(42f, (float) Carrier.components(methodType)[0].invokeExact((Object) carrier))
    );
  }

  @Test
  public void carrierD() throws Throwable {
    var methodType = MethodType.methodType(Object.class, double.class);
    var constructor = Carrier.constructor(methodType);
    var carrier = constructor.invokeExact(42.);
    assertAll(
        () -> assertEquals(long.class, carrier.getClass().getDeclaredField("l0").getType()),
        () -> assertEquals(MethodType.methodType(double.class, Object.class), Carrier.component(methodType, 0).type()),
        () -> assertEquals(42., (double) Carrier.component(methodType, 0).invokeExact((Object) carrier)),
        () -> assertEquals(MethodType.methodType(double.class, Object.class), Carrier.components(methodType)[0].type()),
        () -> assertEquals(42., (double) Carrier.components(methodType)[0].invokeExact((Object) carrier))
    );
  }

  @Test
  public void carrierL() throws Throwable {
    var methodType = MethodType.methodType(Object.class, String.class);
    var constructor = Carrier.constructor(methodType);
    var carrier = constructor.invokeExact("42");
    assertAll(
        () -> assertEquals(Object.class, carrier.getClass().getDeclaredField("o0").getType()),
        () -> assertEquals(MethodType.methodType(String.class, Object.class), Carrier.component(methodType, 0).type()),
        () -> assertEquals("42", (String) Carrier.component(methodType, 0).invokeExact((Object) carrier)),
        () -> assertEquals(MethodType.methodType(String.class, Object.class), Carrier.components(methodType)[0].type()),
        () -> assertEquals("42", (String) Carrier.components(methodType)[0].invokeExact((Object) carrier))
    );
  }

  @Test
  public void carrierLDLI() throws Throwable {
    var methodType = MethodType.methodType(Object.class, String.class, double.class, Integer.class, int.class);
    var constructor = Carrier.constructor(methodType);
    var carrier = constructor.invokeExact("42", 42.0, (Integer) 42, 42);

    for(var i = 0; i < methodType.parameterCount(); i++) {
      var expectedMethodType = MethodType.methodType(methodType.parameterType(i), Object.class);
      assertEquals(expectedMethodType, Carrier.component(methodType, i).type());
      assertEquals(expectedMethodType, Carrier.components(methodType)[i].type());
    }

    assertAll(
        () -> assertEquals(Object.class, carrier.getClass().getDeclaredField("o0").getType()),
        () -> assertEquals(Object.class, carrier.getClass().getDeclaredField("o1").getType()),
        () -> assertEquals(int.class, carrier.getClass().getDeclaredField("i0").getType()),
        () -> assertEquals(long.class, carrier.getClass().getDeclaredField("l0").getType()),
        () -> assertEquals("42", (String) Carrier.component(methodType, 0).invokeExact((Object) carrier)),
        () -> assertEquals(42., (double) Carrier.component(methodType, 1).invokeExact((Object) carrier)),
        () -> assertEquals(42, (Integer) Carrier.component(methodType, 2).invokeExact((Object) carrier)),
        () -> assertEquals(42, (int) Carrier.component(methodType, 3).invokeExact((Object) carrier)),
        () -> assertEquals("42", (String) Carrier.components(methodType)[0].invokeExact((Object) carrier)),
        () -> assertEquals(42.0, (double) Carrier.components(methodType)[1].invokeExact((Object) carrier)),
        () -> assertEquals(42, (Integer) Carrier.components(methodType)[2].invokeExact((Object) carrier)),
        () -> assertEquals(42, (int) Carrier.components(methodType)[3].invokeExact((Object) carrier))
    );
  }

  @Test
  public void carrierDDLJJ() throws Throwable {
    var methodType = MethodType.methodType(Object.class, double.class, double.class, String.class, long.class, long.class);
    var constructor = Carrier.constructor(methodType);
    var carrier = constructor.invokeExact(42.0, 43.0, "42", 42L, 43L);

    for(var i = 0; i < methodType.parameterCount(); i++) {
      var expectedMethodType = MethodType.methodType(methodType.parameterType(i), Object.class);
      assertEquals(expectedMethodType, Carrier.component(methodType, i).type());
      assertEquals(expectedMethodType, Carrier.components(methodType)[i].type());
    }

    assertAll(
        () -> assertEquals(Object.class, carrier.getClass().getDeclaredField("o0").getType()),
        () -> assertEquals(long.class, carrier.getClass().getDeclaredField("l0").getType()),
        () -> assertEquals(long.class, carrier.getClass().getDeclaredField("l1").getType()),
        () -> assertEquals(long.class, carrier.getClass().getDeclaredField("l2").getType()),
        () -> assertEquals(long.class, carrier.getClass().getDeclaredField("l3").getType()),
        () -> assertEquals(42., (double) Carrier.component(methodType, 0).invokeExact((Object) carrier)),
        () -> assertEquals(43., (double) Carrier.component(methodType, 1).invokeExact((Object) carrier)),
        () -> assertEquals("42", (String) Carrier.component(methodType, 2).invokeExact((Object) carrier)),
        () -> assertEquals(42L, (long) Carrier.component(methodType, 3).invokeExact((Object) carrier)),
        () -> assertEquals(43L, (long) Carrier.component(methodType, 4).invokeExact((Object) carrier)),
        () -> assertEquals(42., (double) Carrier.components(methodType)[0].invokeExact((Object) carrier)),
        () -> assertEquals(43., (double) Carrier.components(methodType)[1].invokeExact((Object) carrier)),
        () -> assertEquals("42", (String) Carrier.components(methodType)[2].invokeExact((Object) carrier)),
        () -> assertEquals(42L, (long) Carrier.components(methodType)[3].invokeExact((Object) carrier)),
        () -> assertEquals(43L, (long) Carrier.components(methodType)[4].invokeExact((Object) carrier))
    );
  }

  @Test
  public void carrierIDOFF() throws Throwable {
    var methodType = MethodType.methodType(Object.class, int.class, double.class, String.class, float.class, float.class);
    var constructor = Carrier.constructor(methodType);
    var carrier = constructor.invokeExact(42, 42.0, "42", 42f, 43f);

    for(var i = 0; i < methodType.parameterCount(); i++) {
      var expectedMethodType = MethodType.methodType(methodType.parameterType(i), Object.class);
      assertEquals(expectedMethodType, Carrier.component(methodType, i).type());
      assertEquals(expectedMethodType, Carrier.components(methodType)[i].type());
    }

    assertAll(
        () -> assertEquals(Object.class, carrier.getClass().getDeclaredField("o0").getType()),
        () -> assertEquals(int.class, carrier.getClass().getDeclaredField("i0").getType()),
        () -> assertEquals(int.class, carrier.getClass().getDeclaredField("i1").getType()),
        () -> assertEquals(int.class, carrier.getClass().getDeclaredField("i2").getType()),
        () -> assertEquals(long.class, carrier.getClass().getDeclaredField("l0").getType()),
        () -> assertEquals(42, (int) Carrier.component(methodType, 0).invokeExact((Object) carrier)),
        () -> assertEquals(42., (double) Carrier.component(methodType, 1).invokeExact((Object) carrier)),
        () -> assertEquals("42", (String) Carrier.component(methodType, 2).invokeExact((Object) carrier)),
        () -> assertEquals(42f, (float) Carrier.component(methodType, 3).invokeExact((Object) carrier)),
        () -> assertEquals(43f, (float) Carrier.component(methodType, 4).invokeExact((Object) carrier)),
        () -> assertEquals(42, (int) Carrier.components(methodType)[0].invokeExact((Object) carrier)),
        () -> assertEquals(42., (double) Carrier.components(methodType)[1].invokeExact((Object) carrier)),
        () -> assertEquals("42", (String) Carrier.components(methodType)[2].invokeExact((Object) carrier)),
        () -> assertEquals(42f, (float) Carrier.components(methodType)[3].invokeExact((Object) carrier)),
        () -> assertEquals(43f, (float) Carrier.components(methodType)[4].invokeExact((Object) carrier))
    );
  }
}