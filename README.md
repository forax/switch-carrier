# switch-carrier

Java compiler translation strategy for a swith with some destructing patterns need a data structure to store and load the binding values during the pattern matching

Let suppose we have the following code
```java
record Point(int x, int y) {}
record Rectangle(Point p1, Point p2) {}
...
switch(value) {
  case Point(int a, int b) -> a + b;
  case Rectangle(Point(a, _), Point p) -> a + p.y;
   ...
}
```

The code should be translated to
```java
Object carrier = invokedynamic match(value)Ljava/lang/Object; ["Point(??)|Rectangle(Point(?_)?)", "IIIILPoint;"]
switch(invokedynamic component(carrier)I [0, MethodType(IIILPoint;)Ljava/lang/Object;]) {
  case 0 -> invokedynamic component(carrier)I [1, MethodType(IIILPoint;)Ljava/lang/Object;] +
            invokedynamic component(carrier)I [2, MethodType(IIILPoint;)Ljava/lang/Object;];
  case 1 -> invokedynamic component(carrier)I [1, MethodType(IIILPoint;)Ljava/lang/Object;] +
            invokedynamic component(carrier)LPoint; [3, MethodType(IIILPoint;)Ljava/lang/Object;].y;
  ...           
}
```

First, for each pattern, the type of the biding are extracted
- `case Point(int a, int b)` => (int, int)
- `case Rectangle(Point(a, _), Point p)` => (int, Point)

from that we can determine the types needed for the carrier and the component index,
first we need an index to indicate the index of the matching pattern (:0 int)
so we need a carrier(int, int, int Point) with
- `case Point(int a, int b)` => from carrier (:1 int, :2 int)
- `case Rectangle(Point(a, _), Point p)` => from carrier (:1 int, :3 Point)

The pattern is encoded as a string with the convention that '?' is a binding, '_' is a hole, `|` means __or__, a name is a type pattern, '(' and ')' enter and exit a sub-pattern
so the encoding here is
```
  "Point(??)|Rectangle(Point(?_)?)"
```
and the type of each binding/hole in order is "IIIILPoint;"

In term of implementation, a carrier class is generated at runtime, to avoid to generate to many classes,
the types are erased to Object, int or long. All objects are erased to Object, byte, short, char, int, float are earsed to int, long and double are erased to long.


  

  

