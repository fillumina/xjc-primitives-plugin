# xjc-primitives-plugin

An XJC plugin that replaces the primitive type of the generated fields with the matching boxed
class, and follows the change through the getter and the setter. A constraint cannot be put on a
primitive, so a schema whose field is `xs:int` has to become `Integer` before a bean validation
annotation can be attached to it.

```java
protected int count;          // what XJC generates
protected Integer count;      // what this plugin generates
```

The plugin was split out of [`com.fillumina:krasa-jaxb-tools`](
https://github.com/fillumina/krasa-jaxb-tools), which continues the XJC addon that Vojtech Krasa
wrote; the code here is a port of that one. It becomes its own artifact so that a project that only
needs the boxed types does not depend on the validation annotations as well, and so that adding it
does not bring the bean validation plugin with it.

## Requirements

- JDK 21 or newer.
- An XJC 4 build, that is Jakarta XML Binding 4. The plugin is jakarta-only and has no `javax`
  flavour.

## Using it

With the XJC command line:

```
xjc -extension -XReplacePrimitives schema.xsd
```

The option must be given before the plugins that copy the property types into other methods, such
as the ones generating `equals` and `hashCode`, because those copy whatever type the field has at
the time they run.

The `include` and `exclude` options narrow which fields are boxed, see
[Selecting the fields](#selecting-the-fields).

Inside a Maven build the plugin goes on the classpath of whatever runs XJC, which with the
`jaxb-maven-plugin` means declaring it as a dependency of that plugin and passing
`-XReplacePrimitives` among the arguments.

## Selecting the fields

Without a selector every primitive the plugin finds is boxed. The `include` and `exclude` options
narrow that, one selector at a time, and both are additive: repeat the option to add a selector.

The plain `-XReplacePrimitives` is still required. XJC switches a plugin on when it sees the option
name on its own, and an argument such as `-XReplacePrimitives:include=…` only configures a plugin
that has already been switched on: given alone it does nothing at all.

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

A selector is `ClassGlob[#fieldGlob]`:

| Part         | Meaning                                                                                                                                                                             |
| ------------ | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `ClassGlob`  | Qualified name of the generated class. `*` matches any sequence, `?` one character, and `[...]` one character of a set, of a range, or of a negated set, as in `[abc]`, `[a-z]` and `[!0-9]`; everything else is literal, so the `.` of a qualified name is not the "any character" of a pattern. |
| `#fieldGlob` | Optional name of the field. Without it the selector covers every field of the class.                                                                                                 |

With at least one `include`, only the fields it selects are boxed. An `exclude` takes fields out
of that result whatever the includes say.

A selector is an error in two cases, and either one stops the build:

- it is not a selector a glob can read, as `*#item[9-0]`; or an option carries no value; or an option
  names something other than `include` and `exclude`. This is refused while the options are read,
  before anything is generated.
- it names no class and no field. This is refused at the end of the run, once every class of the
  schema has been seen and the verdict is sure, because until then a class it names may still be
  coming.

A field XJC has already boxed, as an optional attribute is, needs nothing from the plugin: a
selector naming it is not a typo and is not reported.

The glob syntax is the one of the `override` option of the sibling
[`xjc-bean-validation-plugin`](https://github.com/fillumina/xjc-bean-validation-plugin), and the
matcher is copied from it.

### What a field left alone means

It keeps the type XJC generated, so `xs:int` stays `int` and the getter and setter follow:
`getAmount(): int`, `setAmount(int)`. That is a change in the generated API, not only in the
annotations.

The bean validation plugin still computes the annotations of that property, `@NotNull` in
particular, and writes them on the primitive field. Hibernate Validator 8.0.1 validates such a bean
without a complaint, so nothing breaks at runtime, but a constraint on a primitive can never fail:
the null check the schema asked for is gone. Exclude a field when the primitive is what is wanted,
not to silence an annotation.

## Building

The build needs JDK 21 and Maven, and nothing else. With both on the path:

```
mvn -B verify
```
