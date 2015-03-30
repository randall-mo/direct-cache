Direct-Cache store the cached values in jvm direct memory(offheap), which is out of gc management, with this lib you can cache huge data and be free of gc pause problem.

Cache'key still in jvm heap. The cache value should be large enough(> 1k), so the direct cache will really help

####Get direct-cache
For maven users, add:
```xml
<dependency>
    <groupId>net.dongliu</groupId>
    <artifactId>direct-cache</artifactId>
    <version>0.4.10</version>
</dependency>
```
to your pom file.

####Usage
Get an instance:
```java
 DirectCache cache = DirectCache.newBuilder().maxMemorySize(Size.Gb(100)).build();
```
Put and retrieve
```java
// add one entry
cache.set("test", "value");
// get value
Value<String> value = cache.get("test", String.class);
if (value != null) {
    String str = value.getValue();
} else {
    // key not exists in cache
}
```
