# Thymeleaf
To build a Spring Boot web application using Thymeleaf views, follow the below-mentioned steps: 

## Add Thymeleaf Starter

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-thymeleaf</artifactId>
</dependency>
```

Use Thymeleaf HTML namespace as follows:

```html
<!DOCTYPE html>
<html lang="en"
      xmlns="http://www.w3.org/1999/xhtml"
      xmlns:th="http://www.thymeleaf.org">
<head>
    <title>Page Title</title>
</head>
<body>
<div>
    <h1 th:text="${modelAttribute}">Placeholder Text</h1>
</div>
</body>
</html>
```

## Add Thymeleaf Layout Dialect

To create a common page layout, use `thymeleaf-layout-dialect` by adding the following dependency:

```xml
<dependency>
    <groupId>nz.net.ultraq.thymeleaf</groupId>
    <artifactId>thymeleaf-layout-dialect</artifactId>
</dependency>
```

Create a layout template (`src/main/resources/templates/layout.html`) as follows:

```html
<!DOCTYPE html>
<html lang="en"
      xmlns="http://www.w3.org/1999/xhtml"
      xmlns:th="http://www.thymeleaf.org"
      xmlns:layout="http://www.ultraq.net.nz/thymeleaf/layout">
<head>
    <meta charset="utf-8"/>
    <meta http-equiv="X-UA-Compatible" content="IE=edge"/>
    <title layout:title-pattern="$LAYOUT_TITLE - $CONTENT_TITLE">ProjectName</title>
    <meta content="width=device-width, initial-scale=1, maximum-scale=1, user-scalable=no" name="viewport"/>
    <div layout:fragment="pageStyles"></div>
</head>
<body>
<div id="app" class="container">
    <div layout:fragment="content">
        <!-- Your Page Content Here -->
    </div>
</div>
</main>
<div layout:fragment="pageScripts">
</div>
</body>
</html>
```

Create a Thymeleaf view template using the layout template as follows:

```html
<!DOCTYPE html>
<html lang="en"
      xmlns="http://www.w3.org/1999/xhtml"
      xmlns:th="http://www.thymeleaf.org"
      xmlns:layout="http://www.ultraq.net.nz/thymeleaf/layout"
      layout:decorate="~{layout}">
<head>
    <title>Page Title</title>
</head>
<body>
<div layout:fragment="content">
    <div>
        Page specific content
    </div>
</div>
</body>
</html>
```

## Add Thymeleaf Spring Security Support

To use Spring Security support in Thymeleaf templates, add the following dependency:

```xml
<dependency>
    <groupId>org.thymeleaf.extras</groupId>
    <artifactId>thymeleaf-extras-springsecurity6</artifactId>
</dependency>
```

Use Security namespace as follows:

```html
<!DOCTYPE html>
<html lang="en"
      xmlns="http://www.w3.org/1999/xhtml"
      xmlns:th="http://www.thymeleaf.org"
      xmlns:sec="http://www.thymeleaf.org/extras/spring-security">

</html>
```

Use `sec:authorize` attribute to check authentication status and role check:

```html
<div sec:authorize="isAnonymous()">
    Content to show for unauthenticated user
</div>

<div sec:authorize="isAuthenticated()">
    Content to show for authenticated user
</div>

<div sec:authorize="hasRole('ADMIN')">
    Content to show for authenticated user with ADMIN ROLE
</div>

<div sec:authorize="hasAnyRole('USER', 'ADMIN')">
    Content to show for authenticated user with either USER or ADMIN ROLE
</div>
```

Use `sec:authentication` to access the authenticated `Principal` object.

```html
<span sec:authentication="principal.username">userName</span>
```
