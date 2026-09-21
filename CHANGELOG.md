# Changelog

## 1.0.0-SNAPSHOT

- First release of the standalone plugin. It was carried inside `com.fillumina:krasa-jaxb-tools`
  until 2.8.0, in the same jar and the same service file as the bean validation plugin.
- Jakarta only: built for JDK 21 and XJC 4, the Jakarta XML Binding 4 line. There is no `javax`
  flavour.
- The service file registers this plugin alone, so adding it does not bring the bean validation
  plugin with it.
- The option name is unchanged: `-XReplacePrimitives`.
