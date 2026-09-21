# xjc-primitives-plugin

An XJC plugin that replaces the primitive type of the generated fields with the matching boxed
class, and follows the change through the getter and the setter. A constraint cannot be put on a
primitive, so a schema whose field is `xs:int` has to become `Integer` before a bean validation
annotation can be attached to it.

```java
protected int count;          // what XJC generates
protected Integer count;      // what this plugin generates
```

The plugin is the primitive half of the old `com.fillumina:krasa-jaxb-tools`, split out into its
own artifact so that a project that only needs the boxed types does not depend on the validation
annotations as well.

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

Inside a Maven build the plugin goes on the classpath of whatever runs XJC, which with the
`jaxb-maven-plugin` means declaring it as a dependency of that plugin and passing
`-XReplacePrimitives` among the arguments.

## Building

The build needs nix. `nix-shell` gives JDK 21 and Maven:

```
nix-shell --run 'mvn -B verify'
```
