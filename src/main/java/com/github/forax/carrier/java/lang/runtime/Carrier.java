/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

package com.github.forax.carrier.java.lang.runtime;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.invoke.MethodHandles.Lookup.ClassOption;
import java.lang.invoke.MethodType;
import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Type;

import static org.objectweb.asm.Opcodes.*;

/**
 * This  class is used to create objects that have number and types of
 * components determined at runtime.
 */
public final class Carrier {
    /**
     * Lookup used to define and reference the carrier object classes.
     */
    private static final Lookup LOOKUP;

    /**
     * Maximum number of components in a carrier (based on the maximum
     * number of args to a constructor.)
     */
    private static final int MAX_COMPONENTS = 254;

    /**
     * Maximum number of components in a CarrierClass.
     */
    private static final int MAX_OBJECT_COMPONENTS = 32;

    /*
     * Initialize {@link MethodHandle} constants.
     */
    static {
        Lookup lookup = MethodHandles.lookup();
        LOOKUP = lookup;
        try {
            FLOAT_TO_INT = lookup.findStatic(Float.class, "floatToRawIntBits",
                    MethodType.methodType(int.class, float.class));
            INT_TO_FLOAT = lookup.findStatic(Float.class, "intBitsToFloat",
                    MethodType.methodType(float.class, int.class));
            DOUBLE_TO_LONG = lookup.findStatic(Double.class, "doubleToRawLongBits",
                    MethodType.methodType(long.class, double.class));
            LONG_TO_DOUBLE = lookup.findStatic(Double.class, "longBitsToDouble",
                    MethodType.methodType(double.class, long.class));

            BOOLEAN_TO_INT = lookup.findStatic(Carrier.class, "booleanToInt",
                    MethodType.methodType(int.class, boolean.class));
            INT_TO_BOOLEAN = lookup.findStatic(Carrier.class, "intToBoolean",
                    MethodType.methodType(boolean.class, int.class));
            BYTE_TO_INT = lookup.findStatic(Carrier.class, "byteToInt",
                    MethodType.methodType(int.class, byte.class));
            INT_TO_BYTE = lookup.findStatic(Carrier.class, "intToByte",
                    MethodType.methodType(byte.class, int.class));
            SHORT_TO_INT = lookup.findStatic(Carrier.class, "shortToInt",
                    MethodType.methodType(int.class, short.class));
            INT_TO_SHORT = lookup.findStatic(Carrier.class, "intToShort",
                    MethodType.methodType(short.class, int.class));
            CHAR_TO_INT = lookup.findStatic(Carrier.class, "charToInt",
                    MethodType.methodType(int.class, char.class));
            INT_TO_CHAR = lookup.findStatic(Carrier.class, "intToChar",
                    MethodType.methodType(char.class, int.class));
        } catch (NoSuchMethodException | IllegalAccessException e) {
            throw new AssertionError(e);
        }
    }

    /*
     * Primitive conversions.
     */

    private static final MethodHandle FLOAT_TO_INT;
    private static final MethodHandle INT_TO_FLOAT;
    private static final MethodHandle DOUBLE_TO_LONG;
    private static final MethodHandle LONG_TO_DOUBLE;
    private static final MethodHandle BOOLEAN_TO_INT;
    private static final MethodHandle INT_TO_BOOLEAN;
    private static final MethodHandle BYTE_TO_INT;
    private static final MethodHandle INT_TO_BYTE;
    private static final MethodHandle SHORT_TO_INT;
    private static final MethodHandle INT_TO_SHORT;
    private static final MethodHandle CHAR_TO_INT;
    private static final MethodHandle INT_TO_CHAR;

    private static int booleanToInt(boolean b) {
        return b ? 1 : 0;
    }

    private static boolean intToBoolean(int i) {
        return i != 0;
    }

    private static int byteToInt(byte b) {
        return b;
    }

    private static byte intToByte(int i) {
        return (byte)i;
    }

    private static int shortToInt(short s) {
        return s;
    }

    private static short intToShort(int i) {
        return (short)i;
    }

    private static int charToInt(char c) {
        return c;
    }

    private static char intToChar(int i) {
        return (char)i;
    }

    /**
     * Object signature descriptor.
     */
    private static final String OBJECT_DESCRIPTOR =
            Type.getDescriptor(Object.class);

    /**
     * int signature descriptor.
     */
    private static final String INT_DESCRIPTOR =
            Type.getDescriptor(int.class);

    /**
     * long signature descriptor.
     */
    private static final String LONG_DESCRIPTOR =
            Type.getDescriptor(long.class);

    /**
     * Shape of carrier based on counts of each of the three fundamental data
     * types.
     */
    private record CarrierShape(int objectCount, int intCount, int longCount) {
        /**
         * Total number of slots used in a {@link CarrierClass} instance.
         *
         * @return number of slots used
         */
        int slotCount() {
            return objectCount + intCount + longCount * 2;
        }

        /**
         * Returns index of first object component.
         *
         * @return index of first object component
         */
        int objectOffset() {
            return 0;
        }

        /**
         * Returns index of first int component.
         *
         * @return index of first int component
         */
        int intOffset() {
            return objectCount;
        }


        /**
         * Returns index of first long component.
         *
         * @return index of first long component
         */
        int longOffset() {
            return objectCount + intCount;
        }

        /**
         * Returns the constructor method type.
         *
         * @return the constructor method type.
         */
        MethodType constructorMethodType() {
            Class<?>[] array = new Class<?>[objectCount + intCount + longCount];
            int arg = 0;
            for(int i = 0; i < objectCount; i++) {
                array[arg++] = Object.class;
            }
            for(int i = 0; i < intCount; i++) {
                array[arg++] = int.class;
            }
            for(int i = 0; i < longCount; i++) {
                array[arg++] = long.class;
            }
          return MethodType.methodType(void.class, array);
        }
    }

    /**
     * Factory for array based carrier. Array wrapped in object to provide
     * immutability.
     */
    static class CarrierArrayFactory {
        /**
         * Constructor
         *
         * @param ptypes types of carrier constructor arguments
         *
         * @return {@link MethodHandle} to generic carrier constructor.
         */
        static MethodHandle constructor(Class<?>[] ptypes) {
            MethodType methodType = MethodType.methodType(Object.class, ptypes);
            MethodHandle collector = MethodHandles.identity(Object[].class)
                    .withVarargs(true);

            return collector.asType(methodType);
        }

        /**
         * Return an array of carrier component getters, aligning with types in
         * {@code ptypes}.
         *
         * @param ptypes  types of carrier constructor arguments
         *
         * @return array of carrier getters
         */
        static MethodHandle[] components(Class<?>[] ptypes) {
            int length = ptypes.length;
            MethodHandle[] getters = new MethodHandle[length];

            for (int i = 0; i < length; i++) {
                getters[i] = component(ptypes, i);
            }

            return getters;
        }

        /**
         * Return a carrier getter for component {@code i}.
         *
         * @param ptypes  types of carrier constructor arguments
         * @param i       index of parameter to get
         *
         * @return carrier component {@code i} getter {@link MethodHandle}
         */
        static MethodHandle component(Class<?>[] ptypes, int i) {
            MethodType methodType =
                    MethodType.methodType(ptypes[i], Object.class);
            MethodHandle getter =
                    MethodHandles.arrayElementGetter(Object[].class);

            return MethodHandles.insertArguments(
                    getter, 1, i).asType(methodType);
        }
    }

    /**
     * Factory for object based carrier.
     */
    static class CarrierObjectFactory {
        /**
         * Define the hidden class Lookup object
         *
         * @param bytes  class content
         *
         * @return the Lookup object of the hidden class
         */
        static Lookup defineHiddenClass(byte[] bytes) {
            try {
                return LOOKUP.defineHiddenClass(bytes, false, ClassOption.STRONG);
            } catch (IllegalAccessException e) {
                throw new AssertionError(e);
            }
        }

        /**
         * Generate the name of an object component.
         *
         * @param index field/component index
         *
         * @return name of object component
         */
        private static String objectFieldName(int index) {
            return "o" + index;
        }

        /**
         * Generate the name of an int component.
         *
         * @param index field/component index
         *
         * @return name of int component
         */
        private static String intFieldName(int index) {
            return "i" + index;
        }

        /**
         * Generate the name of a long component.
         *
         * @param index field/component index
         *
         * @return name of long component
         */
        private static String longFieldName(int index) {
            return "l" + index;
        }

        /**
         * Generate the full name of a carrier class based on shape.
         *
         * @param carrierShape  shape of carrier
         *
         * @return name of a carrier class
         */
        static String carrierClassName(CarrierShape carrierShape) {
            String packageName = Carrier.class.getPackageName().replace('.', '/');
            String className = "Carrier$" +
                    objectFieldName(carrierShape.objectCount) +
                    intFieldName(carrierShape.intCount) +
                    longFieldName(carrierShape.longCount);

            return packageName.isEmpty() ? className :
                    packageName + "/" + className;
        }

        /**
         * Construct a new object carrier class based on shape.
         *
         * @param carrierShape  shape of carrier
         *
         * @return a {@link CarrierClass} object containing constructor and
         *         component getters.
         */
        static CarrierClass newCarrierClass(CarrierShape carrierShape) {
            String carrierClassName = carrierClassName(carrierShape);

            StringBuilder initDescriptor = new StringBuilder("(");

            ClassWriter cw = new ClassWriter(0);
            cw.visit(V17, ACC_PUBLIC | ACC_FINAL, carrierClassName,
                    null, "java/lang/Object", null);
            int fieldFlags = ACC_PUBLIC | ACC_FINAL;

            for (int i = 0; i < carrierShape.objectCount; i++) {
                cw.visitField(fieldFlags, objectFieldName(i), OBJECT_DESCRIPTOR,
                        null, null);
                initDescriptor.append(OBJECT_DESCRIPTOR);
            }

            for (int i = 0; i < carrierShape.intCount; i++) {
                cw.visitField(fieldFlags, intFieldName(i), INT_DESCRIPTOR,
                        null, null);
                initDescriptor.append(INT_DESCRIPTOR);
            }

            for (int i = 0; i < carrierShape.longCount; i++) {
                cw.visitField(fieldFlags, longFieldName(i), LONG_DESCRIPTOR,
                        null, null);
                initDescriptor.append(LONG_DESCRIPTOR);
            }

            initDescriptor.append(")V");

            {
                MethodVisitor init = cw.visitMethod(ACC_PUBLIC,
                    "<init>", initDescriptor.toString(), null, null);
                init.visitVarInsn(ALOAD, 0);
                init.visitMethodInsn(INVOKESPECIAL,
                    "java/lang/Object", "<init>", "()V", false);

                int arg = 1;
                for (int i = 0; i < carrierShape.objectCount; i++) {
                    init.visitVarInsn(ALOAD, 0);
                    init.visitVarInsn(ALOAD, arg++);
                    init.visitFieldInsn(PUTFIELD, carrierClassName,
                        objectFieldName(i), OBJECT_DESCRIPTOR);
                }

                for (int i = 0; i < carrierShape.intCount; i++) {
                    init.visitVarInsn(ALOAD, 0);
                    init.visitVarInsn(ILOAD, arg++);
                    init.visitFieldInsn(PUTFIELD, carrierClassName,
                        intFieldName(i), INT_DESCRIPTOR);
                }

                for (int i = 0; i < carrierShape.longCount; i++) {
                    init.visitVarInsn(ALOAD, 0);
                    init.visitVarInsn(LLOAD, arg);
                    arg += 2;
                    init.visitFieldInsn(PUTFIELD, carrierClassName,
                        longFieldName(i), LONG_DESCRIPTOR);
                }

                init.visitInsn(RETURN);
                init.visitMaxs(
                    carrierShape.longCount != 0? 3: ((carrierShape.objectCount != 0 || carrierShape.intCount != 0)? 2: 1)  ,
                    1 /* this */ + carrierShape.slotCount());
                init.visitEnd();
            }

            cw.visitEnd();
            byte[] bytes = cw.toByteArray();

            Lookup hiddenClassLookup = defineHiddenClass(bytes);
            Class<?> hiddenClass = hiddenClassLookup.lookupClass();
            MethodType constructorMethodType = carrierShape.constructorMethodType();
            MethodHandle constructor;
            MethodHandle[] components;
            try {
                constructor = hiddenClassLookup.findConstructor(hiddenClass, constructorMethodType);

                components = new MethodHandle[constructorMethodType.parameterCount()];
                int arg = 0;
                for(int i = 0; i < carrierShape.objectCount; i++) {
                    components[arg++] = hiddenClassLookup.findGetter(hiddenClass,
                        CarrierObjectFactory.objectFieldName(i), Object.class);
                }
                for(int i = 0; i < carrierShape.intCount; i++) {
                    components[arg++] = hiddenClassLookup.findGetter(hiddenClass,
                        CarrierObjectFactory.intFieldName(i), int.class);
                }
                for(int i = 0; i < carrierShape.longCount; i++) {
                    components[arg++] = hiddenClassLookup.findGetter(hiddenClass,
                        CarrierObjectFactory.longFieldName(i), long.class);
                }
            } catch (NoSuchMethodException | NoSuchFieldException | IllegalAccessException e) {
                throw new AssertionError(e);
            }

            return new CarrierClass(constructor, components);
        }
    }

    /**
     * Provides constructor and component MethodHandles for a constructed
     * carrier class.
     */
    record CarrierClass(
        /**
         * A raw {@link MethodHandle} for a carrier object constructor.
         * This constructor will only have Object, int and long type arguments.
         *
         */
        MethodHandle constructor,

        /**
         * All the raw {@link MethodHandle MethodHandles} for a carrier
         * component getters. These getters will only return Object, int and
         * long types.
         */
        MethodHandle[] components) {

        /**
         * Create a single raw {@link MethodHandle} for a carrier component
         * getter.
         *
         * @param i  index of component to get
         *
         * @return raw {@link MethodHandle} for the component getter
         */
        MethodHandle component(int i) {
            return components[i];
        }
    }

    /**
     * Cache for all constructed carrier object classes, keyed on class
     * name (i.e., carrier shape.)
     */
    private static final ConcurrentHashMap<String, CarrierClass> carrierCache =
            new ConcurrentHashMap<>();

    /**
     * Constructor
     */
    private Carrier() {
    }

    /**
     * Find or create carrier class for a carrioer shape.
     *
     * @param carrierShape  shape of carrier
     *
     * @return {@link Class<>} of carrier class matching carrier shape
     */
    private static CarrierClass findCarrierClass(CarrierShape carrierShape) {
        String carrierClassName =
                CarrierObjectFactory.carrierClassName(carrierShape);

        return carrierCache.computeIfAbsent(carrierClassName,
                cn -> CarrierObjectFactory.newCarrierClass(carrierShape));
    }

    /**
     * Get the carrier shape based on parameter types.
     *
     * @param ptypes  parameter types
     *
     * @return carrier object shape
     *
     * @throws IllegalArgumentException if number of component slots exceeds maximum
     */
    private static CarrierShape getCarrierShape(Class<?>[] ptypes) {
        int objectCount = 0;
        int intCount = 0;
        int longCount = 0;

        for (Class<?> ptype : ptypes) {
            if (!ptype.isPrimitive()) {
                objectCount++;
            } else if (ptype == long.class || ptype == double.class) {
                longCount++;
            } else {
                intCount++;
            }
        }

        CarrierShape carrierShape = new CarrierShape(objectCount, intCount, longCount);

        if (MAX_COMPONENTS < carrierShape.slotCount()) {
            throw new IllegalArgumentException("Exceeds maximum number of component slots");
        }

        return carrierShape;
    }

    /**
     * Permute a raw constructor {@link MethodHandle} to match the order and
     * types of the parameter types.
     *
     * @param newPTypes     given parameter types
     * @param carrierShape  carrier object shape
     * @param constructor   {@link MethodHandle} to raw carrier object constructor
     *
     * @return {@link MethodHandle} constructor matching parameter types
     */
    private static MethodHandle constructor(Class<?>[] newPTypes,
                                            CarrierShape carrierShape,
                                            MethodHandle constructor) {
        int objectIndex = carrierShape.objectOffset();
        int intIndex = carrierShape.intOffset();
        int longIndex = carrierShape.longOffset();
        int[] reorder = new int[newPTypes.length];
        int index = 0;
        Class<?>[] permutePTypes = new Class<?>[newPTypes.length];

        for (Class<?> ptype : newPTypes) {
            MethodHandle filter = null;
            int from;

            if (!ptype.isPrimitive()) {
                from = objectIndex++;
                ptype = Object.class;
            } else if (ptype == long.class || ptype == double.class) {
                from = longIndex++;
                filter = ptype == double.class ? DOUBLE_TO_LONG : null;
            } else {
                from = intIndex++;

                if (ptype == float.class) {
                    filter = FLOAT_TO_INT;
                } else if (ptype == boolean.class) {
                    filter = BOOLEAN_TO_INT;
                } else if (ptype == byte.class) {
                    filter = BYTE_TO_INT;
                } else if (ptype == short.class) {
                    filter = SHORT_TO_INT;
                } else if (ptype == char.class) {
                    filter = CHAR_TO_INT;
                }
            }

            permutePTypes[index] = ptype;
            reorder[from] = index++;
            constructor = filter == null ? constructor :
                    MethodHandles.filterArguments(constructor, from, filter);
        }

        MethodType permutedMethodType =
                MethodType.methodType(constructor.type().returnType(),
                        permutePTypes);
        MethodType castMethodType =
                MethodType.methodType(Object.class, newPTypes);
        constructor = MethodHandles.permuteArguments(constructor,
                permutedMethodType, reorder);
        constructor = constructor.asType(castMethodType);

        return constructor;
    }

    /**
     * Permute raw component getters to match order and types of the parameter
     * types.
     *
     * @param ptypes        given parameter types
     * @param carrierShape  carrier object shape
     * @param components    raw {@link MethodHandle MethodHandles} to raw
     *                      carrier component getters
     *
     * @return array of components matching parameter types
     */
    private static MethodHandle[] components(Class<?>[] ptypes,
                                             CarrierShape carrierShape,
                                             MethodHandle[] components) {
        MethodHandle[] reorder = new MethodHandle[ptypes.length];
        int objectIndex = 0;
        int intIndex = carrierShape.objectCount;
        int longIndex = carrierShape.objectCount + carrierShape.intCount;
        int index = 0;

        for (Class<?> ptype : ptypes) {
            MethodHandle component;
            MethodHandle filter = null;

            if (!ptype.isPrimitive()) {
                component = components[objectIndex++];
            } else if (ptype == long.class || ptype == double.class) {
                component = components[longIndex++];
                filter = ptype == double.class ? LONG_TO_DOUBLE : null;
            } else {
                component = components[intIndex++];

                if (ptype == float.class) {
                    filter = INT_TO_FLOAT;
                } else if (ptype == boolean.class) {
                    filter = INT_TO_BOOLEAN;
                } else if (ptype == byte.class) {
                    filter = INT_TO_BYTE;
                } else if (ptype == short.class) {
                    filter = INT_TO_SHORT;
                } else if (ptype == char.class) {
                    filter = INT_TO_CHAR;
                }
            }

            component = filter == null ? component :
                    MethodHandles.filterReturnValue(component, filter);
            MethodType methodType = MethodType.methodType(ptype, Object.class);
            reorder[index++] = component.asType(methodType);
        }

        return reorder;
    }

    /**
     * Returns a carrier component getter {@link MethodHandle} for the
     * component {@code i}.
     *
     * @param ptypes        given parameter types
     * @param carrierShape  shape of the carrier object
     * @param carrierClass  carrier object class
     * @param i             index to the component
     *
     * @return carrier component getter {@link MethodHandle}
     *
     * @throws IllegalArgumentException if number of component slots exceeds maximum
     */
    private static MethodHandle component(Class<?>[] ptypes,
                                          CarrierShape carrierShape,
                                          CarrierClass carrierClass,
                                          int i) {
        CarrierShape componentShape = getCarrierShape(Arrays.copyOf(ptypes, i));
        Class<?> ptype = ptypes[i];
        int index;
        MethodHandle filter = null;

        if (!ptype.isPrimitive()) {
            index = carrierShape.objectOffset() + componentShape.objectCount();
        } else if (ptype == long.class || ptype == double.class) {
            index = carrierShape.longOffset() + componentShape.longCount();
            filter = ptype == double.class ? LONG_TO_DOUBLE : null;
        } else {
            index = carrierShape.intOffset() + componentShape.intCount();

            if (ptype == float.class) {
                filter = INT_TO_FLOAT;
            } else if (ptype == boolean.class) {
                filter = INT_TO_BOOLEAN;
            } else if (ptype == byte.class) {
                filter = INT_TO_BYTE;
            } else if (ptype == short.class) {
                filter = INT_TO_SHORT;
            } else if (ptype == char.class) {
                filter = INT_TO_CHAR;
            }
        }

        MethodHandle component = carrierClass.component(index);
        component = filter == null ? component :
                MethodHandles.filterReturnValue(component, filter);

        return component.asType(MethodType.methodType(ptype, Object.class));
    }

    /**
     * Return a constructor {@link MethodHandle} for a carrier with components
     * aligning with the parameter types of the supplied
     * {@link MethodType methodType}.
     *
     * @param methodType {@link MethodType} providing types for the carrier's
     *                   components.
     *
     * @return carrier constructor {@link MethodHandle}
     *
     * @throws NullPointerException is methodType is null
     * @throws IllegalArgumentException if number of component slots exceeds maximum
     */
    public static MethodHandle constructor(MethodType methodType) {
        Objects.requireNonNull(methodType);

        Class<?>[] ptypes = methodType.parameterArray();
        CarrierShape carrierShape = getCarrierShape(ptypes);

        if (carrierShape.slotCount() <= MAX_OBJECT_COMPONENTS) {
            CarrierClass carrierClass = findCarrierClass(carrierShape);

            return constructor(ptypes, carrierShape, carrierClass.constructor());
        } else {
            return CarrierArrayFactory.constructor(ptypes);
        }
    }

    /**
     * Return component getter {@link MethodHandle MethodHandles} for all the
     * carrier's components.
     *
     * @param methodType {@link MethodType} providing types for the carrier's
     *                   components.
     *
     * @return  array of get component {@link MethodHandle MethodHandles}
     *
     * @throws NullPointerException is methodType is null
     * @throws IllegalArgumentException if number of component slots exceeds maximum
     *
     */
    public static MethodHandle[] components(MethodType methodType) {
        Objects.requireNonNull(methodType);
        Class<?>[] ptypes = methodType.parameterArray();
        CarrierShape carrierShape = getCarrierShape(ptypes);
        int slotCount = carrierShape.slotCount();
        MethodHandle[] components;

        if (slotCount <= MAX_OBJECT_COMPONENTS) {
            Carrier.CarrierClass carrierClass = findCarrierClass(carrierShape);
            components = components(ptypes, carrierShape, carrierClass.components());
        } else {
            components = Carrier.CarrierArrayFactory.components(ptypes);
        }

        return components;
    }

    /**
     * Return a component getter {@link MethodHandle} for component {@code i}.
     *
     * @param methodType {@link MethodType} providing types for the carrier's
     *                   components.
     * @param i          component index
     *
     * @return a component getter {@link MethodHandle} for component {@code i}
     *
     * @throws NullPointerException is methodType is null
     * @throws IllegalArgumentException if number of component slots exceeds maximum
     *                                  or if {@code i} is out of bounds
     */
    public static MethodHandle component(MethodType methodType, int i) {
        Objects.requireNonNull(methodType);
        Class<?>[] ptypes = methodType.parameterArray();

        if (i < 0 || i >= ptypes.length) {
            throw new IllegalArgumentException("i is out of bounds for ptypes");
        }

        CarrierShape carrierShape = getCarrierShape(ptypes);

        if (carrierShape.slotCount() <= MAX_OBJECT_COMPONENTS) {
            CarrierClass carrierClass = findCarrierClass(carrierShape);

            return component(ptypes, carrierShape, carrierClass, i);
        } else {
            return CarrierArrayFactory.component(ptypes, i);
        }
    }
}
