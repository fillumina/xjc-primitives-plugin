[![Maven Central](https://img.shields.io/maven-central/v/com.fillumina/xjc-primitives-plugin.svg)](https://central.sonatype.com/artifact/com.fillumina/xjc-primitives-plugin)

# xjc-primitives-plugin

An XJC plugin that replaces the primitive type of the generated fields with the matching boxed
class, and follows the change through the getter and the setter. When an XJC `boolean`
`isActive()` becomes `Boolean`, the plugin also adds `getActive(): Boolean`: JavaBeans
`Introspector` does not treat `isActive(): Boolean` as a readable property. The original
`isActive()` remains available, and JAXB field annotations and field-access binding stay intact.
This change applies only when the plugin boxes a primitive boolean; a `Boolean` that XJC
already generated (for example, an optional boolean attribute with `isFlag(): Boolean`)
is left unchanged and may still be write-only to JavaBeans introspection.

A primitive cannot be absent. XJC generates a primitive for a required element, so an element the
document does not carry arrives as `0` or `false`, indistinguishable from a value that was carried,
and `@NotNull` on the field can never fail. The boxed class makes the absence a `null` the
constraint can report.

```java
protected int count;          // what XJC generates
protected Integer count;      // what this plugin generates
```

The plugin was split out of [`com.fillumina:krasa-jaxb-tools`](
https://github.com/fillumina/krasa-jaxb-tools), which continues the XJC addon that Vojtech Krasa
wrote; the code here is a port of that one. It becomes its own artifact so that a project that only
needs the boxed types does not depend on the validation annotations as well, and so that adding it
does not bring the bean validation plugin with it.

The other two pieces of that line are
[`xjc-bean-validation-plugin`](https://github.com/fillumina/xjc-bean-validation-plugin), which
writes the constraints a schema states, and
[`cxf-validation-frontend`](https://github.com/fillumina/cxf-validation-frontend), which puts
`@Valid` on the service interface generated from a WSDL. This plugin is what makes a constraint
such as `@NotNull` mean something on a primitive, so the three are usually used together.

An example of it inside a real build, with the test of that wiring, is
[`xjc-primitives-plugin-example`](https://github.com/fillumina/xjc-primitives-plugin-example).
The three plugins of this line together in one build, which is where the split is shown to do what
the single plugin did, are in
[`xjc-plugins-example`](https://github.com/fillumina/xjc-plugins-example).

## Version and status

The released version is **1.0.0**, published on Maven Central. The option name, its defaults and
the types it writes stay the same within 1.x; a field it boxes or leaves alone in the wrong way is
a bug fixed in a patch release.

## Requirements

- JDK 21 or newer.
- An XJC 4 build, that is Jakarta XML Binding 4. The plugin is jakarta-only and has no `javax`
  flavour.

## Using it

With the XJC command line:

```sh
# every primitive field
xjc -extension -XReplacePrimitives schema.xsd

# only the fields the selectors name
xjc -extension -XReplacePrimitives -XReplacePrimitives:exclude=*#legacy[0-9] schema.xsd
```

The option must be given before the plugins that copy the property types into other methods, such
as the ones generating `equals` and `hashCode`, because those copy whatever type the field has at
the time they run.

The `include` and `exclude` options narrow which fields are boxed, see
[Selecting the fields](#selecting-the-fields).

Inside a Maven build the plugin goes on the classpath of whatever runs XJC, which with the
`jaxb-maven-plugin` means declaring it as a dependency of that plugin and passing
`-XReplacePrimitives` among the arguments:

```xml
<plugin>
  <groupId>org.jvnet.jaxb</groupId>
  <artifactId>jaxb-maven-plugin</artifactId>
  <version>4.0.9</version>
  <executions>
    <execution>
      <goals>
        <goal>generate</goal>
      </goals>
    </execution>
  </executions>
  <configuration>
    <extension>true</extension>
    <args>
      <arg>-XReplacePrimitives</arg>
      <arg>-XReplacePrimitives:exclude=*#legacy[0-9]</arg>
    </args>
    <plugins>
      <plugin>
        <groupId>com.fillumina</groupId>
        <artifactId>xjc-primitives-plugin</artifactId>
        <version>1.0.0</version>
      </plugin>
    </plugins>
  </configuration>
</plugin>
```

Both arguments are needed: the bare option activates the plugin, the one carrying a value configures
it.

## Selecting the fields

Without a selector every primitive the plugin finds is boxed. The `include` and `exclude` options
narrow that: repeat either option to add a selector, and the two are additive.

The plain `-XReplacePrimitives` is still required. XJC switches a plugin on when it sees the option
name on its own, and an argument such as `-XReplacePrimitives:include=…` only configures a plugin
that has already been switched on: given alone it does nothing at all.

### The shape of a selector

```text
-XReplacePrimitives:include=ClassGlob[#fieldGlob]
-XReplacePrimitives:exclude=ClassGlob[#fieldGlob]
```

| Part         | Meaning                                                                              |
| ------------ | ------------------------------------------------------------------------------------ |
| `ClassGlob`  | Qualified name of the generated class.                                               |
| `#fieldGlob` | Optional name of the field. Without it the selector covers every field of the class.  |

Both globs are matched with the same syntax:

- `*` matches any sequence of characters;
- `?` matches exactly one character;
- `[...]` matches one character of a set, of a range, or of a negated set, as in `[abc]`, `[a-z]`
  and `[!0-9]`; inside the brackets `*`, `?` and `\` are literal, and `-` is a range unless it
  comes first or last;
- everything else is literal, so the `.` of a qualified name is not the "any character" of a
  pattern, and a `[` that is never closed is an error rather than a literal.

The syntax is the one of the `override` option of the sibling
[`xjc-bean-validation-plugin`](https://github.com/fillumina/xjc-bean-validation-plugin), and the
matcher is copied from it.

### What the two options do together

With no selector every primitive field is boxed. With at least one `include` only the fields it
selects are boxed, and an `exclude` takes fields out of that result whatever the includes say. The
order does not matter: the includes build a set of fields and the excludes take from it.

| Selectors                                                  | What is boxed                                            |
| ---------------------------------------------------------- | -------------------------------------------------------- |
| none                                                       | every primitive field                                    |
| `include=*#amount`                                         | the `amount` field of every class                        |
| `include=*#amount`<br>`include=*#total`                    | both of them, wherever they are                          |
| `exclude=*#legacy[0-9]`                                    | every primitive field but the numbered legacy ones       |
| `include=com.acme.Invoice`<br>`exclude=com.acme.Invoice#legacyCode` | every field of that class but `legacyCode`      |

### Examples

```sh
# only the amount of every class
xjc -extension -XReplacePrimitives -XReplacePrimitives:include=*#amount schema.xsd

# everything but the numbered legacy fields
xjc -extension -XReplacePrimitives -XReplacePrimitives:exclude=*#legacy[0-9] schema.xsd

# two fields of one class
xjc -extension -XReplacePrimitives \
    -XReplacePrimitives:include=com.acme.Invoice#amount \
    -XReplacePrimitives:include=com.acme.Invoice#total schema.xsd
```

In a Maven build the same arguments go among the arguments of the codegen plugin:

```xml
<args>
  <arg>-XReplacePrimitives</arg>
  <arg>-XReplacePrimitives:exclude=*#legacy[0-9]</arg>
</args>
```

### When a selector is refused, and when it is only a warning

A selector that is not one a glob can read stops the build. So does an option that carries no
value, or that names something other than `include` and `exclude`. All three are refused while the
options are read, before anything is generated, so a mistake in the command line is reported against
the command line.

A selector that is well formed but names no class and no field of this schema is only a warning:

```text
[WARNING] include=*#amout matched no class and no field in this schema
```

The generation goes on, and the fields the selector meant to name keep the type XJC gave them. The
same selectors are often given to several schemas, and a field that only some of them carry is not a
mistake, so this is reported once every class of the schema has been seen, when the verdict is
sure, and the build does not fail. Note that XJC's own `-quiet` hides warnings, so a build that
passes it may print nothing at all.

A field XJC has already boxed, as an optional attribute is, needs nothing from the plugin: a
selector naming it has matched, so it is not reported.

### What a field left alone means

It keeps the type XJC generated, so `xs:int` stays `int` and the getter and setter follow:
`getAmount(): int`, `setAmount(int)`. That is a change in the generated API, not only in the
annotations.

The [`xjc-bean-validation-plugin`](https://github.com/fillumina/xjc-bean-validation-plugin)
still computes the annotations of that property, `@NotNull` in
particular, and writes them on the primitive field. Hibernate Validator validates such a bean
without a complaint, so nothing breaks at runtime, but `@NotNull` on a primitive can never fail:
the null check the schema asked for is gone. Exclude a field when the primitive is what is wanted,
not to silence an annotation.

## Building

The build needs JDK 21 and Maven, and nothing else. With both on the path:

```
mvn -B verify
```
