# Changelog

## 1.0.0-SNAPSHOT

- Boxed boolean properties now also have a `getX(): Boolean` accessor. This keeps the
  existing `isX(): Boolean` API while making the property readable to JavaBeans
  introspection; JAXB field access remains unchanged.

- First release of the standalone plugin. It was carried inside `com.fillumina:krasa-jaxb-tools`
  until 2.8.0, in the same jar and the same service file as the bean validation plugin.
- Jakarta only: built for JDK 21 and XJC 4, the Jakarta XML Binding 4 line. There is no `javax`
  flavour.
- The service file registers this plugin alone, so adding it does not bring the bean validation
  plugin with it.
- The option name is unchanged: `-XReplacePrimitives`.
- New: the `include` and `exclude` options select which fields are boxed, with the glob syntax of
  the sibling bean validation plugin: `-XReplacePrimitives:include=com.acme.Invoice#amount`. Without
  a selector every primitive is boxed, as before; with one, only the fields it selects. A selector
  that cannot be read, and one that names no class and no field, are both errors and stop the
  generation.
