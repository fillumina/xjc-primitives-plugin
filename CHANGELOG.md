# Changelog

## 1.0.0

- Boxed boolean properties now also have a `getX(): Boolean` accessor. This keeps the
  existing `isX(): Boolean` API while making the property readable to JavaBeans
  introspection; JAXB field access remains unchanged.

- First release of the standalone plugin: a new version of the `-XReplacePrimitives` plugin, on the
  modern stack and as an artifact of its own, so that a build takes only what it uses.
  `com.fillumina:krasa-jaxb-tools` continues to carry its own version of the same plugin for the
  stack it serves, in the same jar as its bean validation plugin. The two are independent and
  neither replaces the other.
- Jakarta only: built for JDK 21 and XJC 4, the Jakarta XML Binding 4 line. There is no `javax`
  flavour.
- The service file registers this plugin alone, so adding it does not bring the bean validation
  plugin with it.
- The option name is unchanged: `-XReplacePrimitives`.
- New: the `include` and `exclude` options select which fields are boxed, with the glob syntax of
  the sibling bean validation plugin: `-XReplacePrimitives:include=com.acme.Invoice#amount`. Without
  a selector every primitive is boxed, as before; with one, only the fields it selects. A selector
  that cannot be read is an error and stops the generation before anything is written. A selector
  that names no class and no field of the schema at hand is only a warning, and the build goes on,
  because one set of selectors is often given to several schemas and a field only some of them carry
  is not a mistake.
