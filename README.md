# customer service

## 升级历史

### uk_quick_type

## html

`org.springframework.web.servlet.DispatcherServlet.getHandler`

`org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping`

`org.springframework.web.servlet.mvc.condition.PatternsRequestCondition.getMatchingCondition`

`org.springframework.web.servlet.mvc.condition.PatternsRequestCondition.getMatchingPattern`

下面的配置默认是 false 了，所以访问 /login.html 不 match /login controller

`org.springframework.boot.autoconfigure.web.servlet.WebMvcAutoConfiguration.WebMvcAutoConfigurationAdapter.configurePathMatch`
`org.springframework.boot.autoconfigure.web.servlet.WebMvcProperties.Pathmatch.useSuffixPattern`
