# PLAN.md

## Goal
Migrate Java EE 7 monolith application from WebLogic/JBoss to Quarkus 3 framework
- Reference used: javaee-quarkus.md

## Project Summary
- Type: Maven/Java EE 7
- Files affected: 30 (Java files: 29, config files: 1)
- Estimated complexity: High
- Hardest steps: MDB conversions (InventoryNotificationMDB.java, OrderServiceMDB.java), WebLogic lifecycle listener conversion, manual Flyway replacement

## Steps

### Step 1: Update build configuration
- File: pom.xml
- Action: MODIFY
- What to do: 
  - Change packaging from war → jar
  - Remove javaee-api dependencies
  - Add Quarkus BOM and maven plugin
  - Add extensions: quarkus-arc, quarkus-rest-jackson, quarkus-hibernate-orm, quarkus-jdbc-postgresql, quarkus-flyway, quarkus-smallrye-reactive-messaging-amqp
- Why: Replace Java EE container dependencies with Quarkus runtime
- Depends on: none
- Verify: mvn clean compile succeeds

### Step 2: Create Quarkus application properties
- File: src/main/resources/application.properties
- Action: CREATE
- What to do: Add datasource config, Flyway settings, messaging channels
- Why: Replace persistence.xml and JMS JNDI configuration
- Depends on: Step 1
- Verify: File contains quarkus.* properties

### Step 3: Update persistence producer
- File: src/main/java/com/redhat/coolstore/persistence/Resources.java
- Action: MODIFY
- What to do: Replace javax.* imports with jakarta.*, update CDI patterns
- Why: Java EE → Jakarta EE namespace change
- Depends on: Step 1
- Verify: No javax.* imports remain

### Step 4: Update utilities and producers
- File: src/main/java/com/redhat/coolstore/utils/Producers.java
- Action: MODIFY
- What to do: Replace javax.* imports with jakarta.*, update CDI beans
- Why: Java EE → Jakarta EE namespace change
- Depends on: Step 1
- Verify: No javax.* imports remain

### Step 5: Update data transformers
- File: src/main/java/com/redhat/coolstore/utils/Transformers.java
- Action: MODIFY
- What to do: Replace javax.* imports with jakarta.*
- Why: Java EE → Jakarta EE namespace change
- Depends on: Step 1
- Verify: No javax.* imports remain

### Step 6: ⚠️ COMPLEX — Replace manual Flyway startup
- File: src/main/java/com/redhat/coolstore/utils/DataBaseMigrationStartup.java
- Action: DELETE
- What to do:
  - BEFORE: @Singleton @Startup EJB with @PostConstruct manual Flyway setup
  - AFTER: Delete entire class - replaced by Quarkus Flyway extension auto-migration
  - Remove: All manual Flyway initialization code, @Resource DataSource injection
  - Config: Add quarkus.flyway.migrate-at-start=true to application.properties
- Why: Quarkus Flyway extension handles migration automatically at startup
- Depends on: Step 2
- Verify: File deleted, quarkus.flyway config in application.properties

### Step 7: ⚠️ COMPLEX — Convert WebLogic startup listener
- File: src/main/java/com/redhat/coolstore/utils/StartupListener.java
- Action: MODIFY
- What to do:
  - BEFORE: extends weblogic.application.ApplicationLifecycleListener with postStart/preStop
  - AFTER: @ApplicationScoped bean with void onStart(@Observes StartupEvent), void onStop(@Observes ShutdownEvent)
  - Remove: weblogic.application imports, extends ApplicationLifecycleListener
  - Add: @ApplicationScoped, io.quarkus.runtime.StartupEvent/ShutdownEvent, jakarta.enterprise.event.Observes
  - Convert postStart(ApplicationLifecycleEvent) → onStart(@Observes StartupEvent)
  - Convert preStop(ApplicationLifecycleEvent) → onStop(@Observes ShutdownEvent)
- Why: WebLogic ApplicationLifecycleListener replaced by Quarkus lifecycle events
- Depends on: Step 1
- Verify: No weblogic.* imports, grep for @Observes StartupEvent usage

### Step 8: Update model entities - CatalogItemEntity
- File: java/com/redhat/coolstore/model/CatalogItemEntity.java
- Action: MODIFY
- What to do: Replace javax.persistence.* → jakarta.persistence.*
- Why: Jakarta EE namespace change
- Depends on: Step 1
- Verify: No javax.persistence imports

### Step 9: Update model entities - InventoryEntity
- File: java/com/redhat/coolstore/model/InventoryEntity.java
- Action: MODIFY
- What to do: Replace javax.persistence.* → jakarta.persistence.*
- Why: Jakarta EE namespace change
- Depends on: Step 1
- Verify: No javax.persistence imports

### Step 10: Update model POJOs - Order
- File: java/com/redhat/coolstore/model/Order.java
- Action: MODIFY
- What to do: Replace javax.* imports with jakarta.*
- Why: Jakarta EE namespace change
- Depends on: Step 1
- Verify: No javax.* imports remain

### Step 11: Update model POJOs - OrderItem
- File: java/com/redhat/coolstore/model/OrderItem.java
- Action: MODIFY
- What to do: Replace javax.* imports with jakarta.*
- Why: Jakarta EE namespace change
- Depends on: Step 1
- Verify: No javax.* imports remain

### Step 12: Update model POJOs - Product
- File: java/com/redhat/coolstore/model/Product.java
- Action: MODIFY
- What to do: Replace javax.* imports with jakarta.*
- Why: Jakarta EE namespace change
- Depends on: Step 1
- Verify: No javax.* imports remain

### Step 13: Update model POJOs - Promotion
- File: java/com/redhat/coolstore/model/Promotion.java
- Action: MODIFY
- What to do: Replace javax.* imports with jakarta.*
- Why: Jakarta EE namespace change
- Depends on: Step 1
- Verify: No javax.* imports remain

### Step 14: Update model POJOs - ShoppingCart
- File: java/com/redhat/coolstore/model/ShoppingCart.java
- Action: MODIFY
- What to do: Replace javax.* imports with jakarta.*
- Why: Jakarta EE namespace change
- Depends on: Step 1
- Verify: No javax.* imports remain

### Step 15: Update model POJOs - ShoppingCartItem
- File: java/com/redhat/coolstore/model/ShoppingCartItem.java
- Action: MODIFY
- What to do: Replace javax.* imports with jakarta.*
- Why: Jakarta EE namespace change
- Depends on: Step 1
- Verify: No javax.* imports remain

### Step 16: Convert EJB service - CatalogService
- File: java/com/redhat/coolstore/service/CatalogService.java
- Action: MODIFY
- What to do: Replace @Stateless → @ApplicationScoped, @EJB → @Inject, jakarta.* imports
- Why: Convert EJB to CDI bean
- Depends on: Step 8, Step 9
- Verify: No @Stateless, @EJB annotations remain

### Step 17: Convert EJB service - OrderService
- File: java/com/redhat/coolstore/service/OrderService.java
- Action: MODIFY
- What to do: Replace @Stateless → @ApplicationScoped, @EJB → @Inject, jakarta.* imports
- Why: Convert EJB to CDI bean
- Depends on: Step 10, Step 11
- Verify: No @Stateless, @EJB annotations remain

### Step 18: Convert EJB service - ProductService
- File: java/com/redhat/coolstore/service/ProductService.java
- Action: MODIFY
- What to do: Replace @Stateless → @ApplicationScoped, @EJB → @Inject, jakarta.* imports
- Why: Convert EJB to CDI bean
- Depends on: Step 12
- Verify: No @Stateless, @EJB annotations remain

### Step 19: Convert EJB service - PromoService
- File: java/com/redhat/coolstore/service/PromoService.java
- Action: MODIFY
- What to do: Replace @Stateless → @ApplicationScoped, @EJB → @Inject, jakarta.* imports
- Why: Convert EJB to CDI bean
- Depends on: Step 13
- Verify: No @Stateless, @EJB annotations remain

### Step 20: Convert remote service interface - ShippingServiceRemote
- File: java/com/redhat/coolstore/service/ShippingServiceRemote.java
- Action: MODIFY
- What to do: Remove @Remote annotation, update imports to jakarta.*
- Why: Remote interfaces not needed in Quarkus
- Depends on: Step 1
- Verify: No @Remote annotation

### Step 21: Convert EJB service - ShippingService
- File: java/com/redhat/coolstore/service/ShippingService.java
- Action: MODIFY
- What to do: Replace @Stateless → @ApplicationScoped, remove @Remote, @EJB → @Inject
- Why: Convert EJB to CDI bean
- Depends on: Step 20
- Verify: No @Stateless, @Remote, @EJB annotations remain

### Step 22: Convert cart processor
- File: java/com/redhat/coolstore/service/ShoppingCartOrderProcessor.java
- Action: MODIFY
- What to do: Replace @Stateless → @ApplicationScoped, @EJB → @Inject, jakarta.* imports
- Why: Convert EJB to CDI bean
- Depends on: Step 17
- Verify: No @Stateless, @EJB annotations remain

### Step 23: Convert EJB service - ShoppingCartService
- File: java/com/redhat/coolstore/service/ShoppingCartService.java
- Action: MODIFY
- What to do: Replace @Stateless → @ApplicationScoped, @EJB → @Inject, jakarta.* imports
- Why: Convert EJB to CDI bean
- Depends on: Step 14, Step 15
- Verify: No @Stateless, @EJB annotations remain

### Step 24: ⚠️ COMPLEX — Convert MDB to reactive messaging
- File: src/main/java/com/redhat/coolstore/service/InventoryNotificationMDB.java
- Action: MODIFY
- What to do:
  - BEFORE: Manual MessageListener with WebLogic JNDI setup, TopicConnection management
  - AFTER: @ApplicationScoped with @Incoming("inventory-notifications") reactive messaging
  - Remove: MessageListener interface, all JMS/JNDI imports, manual connection code
  - Add: @ApplicationScoped, @Incoming, SmallRye reactive messaging imports
  - Convert onMessage(Message) → processInventoryNotification(String orderJson)
  - Keep business logic for inventory threshold checking
  - Update application.properties with messaging channel config
- Why: WebLogic JMS manual setup replaced by Quarkus reactive messaging
- Depends on: Step 2, Step 16 (CatalogService)
- Verify: No MessageListener, no weblogic.jndi imports, grep for @Incoming usage

### Step 25: ⚠️ COMPLEX — Convert MDB to reactive messaging
- File: src/main/java/com/redhat/coolstore/service/OrderServiceMDB.java
- Action: MODIFY
- What to do:
  - BEFORE: @MessageDriven(activationConfig) with topic/orders destination
  - AFTER: @ApplicationScoped with @Incoming("order-topic") reactive messaging
  - Remove: @MessageDriven, ActivationConfigProperty, MessageListener interface
  - Add: @ApplicationScoped, @Incoming, SmallRye reactive messaging imports
  - Convert onMessage(Message) → processOrder(String orderJson)
  - Keep business logic for order processing and inventory updates
  - Update application.properties with mp.messaging.incoming.order-topic config
- Why: EJB @MessageDriven replaced by Quarkus reactive messaging
- Depends on: Step 2, Step 17 (OrderService), Step 16 (CatalogService)
- Verify: No @MessageDriven, no ActivationConfigProperty, grep for @Incoming usage

### Step 26: Update REST application
- File: java/com/redhat/coolstore/rest/RestApplication.java
- Action: MODIFY
- What to do: Replace javax.ws.rs.* → jakarta.ws.rs.*, remove @ApplicationPath if using Quarkus REST
- Why: Jakarta namespace and Quarkus auto-discovery
- Depends on: Step 1
- Verify: No javax.ws.rs imports

### Step 27: Convert REST endpoint - CartEndpoint
- File: java/com/redhat/coolstore/rest/CartEndpoint.java
- Action: MODIFY
- What to do: Replace @Stateless → @ApplicationScoped, @EJB → @Inject, javax.* → jakarta.*
- Why: Convert EJB REST service to CDI
- Depends on: Step 23, Step 26
- Verify: No @Stateless, @EJB annotations

### Step 28: Convert REST endpoint - OrderEndpoint
- File: java/com/redhat/coolstore/rest/OrderEndpoint.java
- Action: MODIFY
- What to do: Replace @Stateless → @ApplicationScoped, @EJB → @Inject, javax.* → jakarta.*
- Why: Convert EJB REST service to CDI
- Depends on: Step 17, Step 26
- Verify: No @Stateless, @EJB annotations

### Step 29: Convert REST endpoint - ProductEndpoint
- File: java/com/redhat/coolstore/rest/ProductEndpoint.java
- Action: MODIFY
- What to do: Replace @Stateless → @ApplicationScoped, @EJB → @Inject, javax.* → jakarta.*
- Why: Convert EJB REST service to CDI
- Depends on: Step 16, Step 26
- Verify: No @Stateless, @EJB annotations

### Step 30: Delete WebLogic stub classes
- File: src/main/java/weblogic/application/ApplicationLifecycleEvent.java
- Action: DELETE
- What to do: Remove entire file
- Why: WebLogic-specific class not needed in Quarkus
- Depends on: Step 7
- Verify: File does not exist

### Step 31: Delete WebLogic stub classes
- File: src/main/java/weblogic/application/ApplicationLifecycleListener.java
- Action: DELETE
- What to do: Remove entire file
- Why: WebLogic-specific class not needed in Quarkus
- Depends on: Step 7
- Verify: File does not exist

### Step 32: Delete WebLogic stub classes
- File: src/main/java/weblogic/i18n/logging/NonCatalogLogger.java
- Action: DELETE
- What to do: Remove entire file
- Why: WebLogic-specific class not needed in Quarkus
- Depends on: none
- Verify: File does not exist

## Verification
```bash
mvn clean compile
mvn quarkus:dev
```

## Notes
- Classic Java EE 7 WebLogic application migration to Quarkus
- InventoryNotificationMDB uses manual WebLogic JMS setup (complex conversion needed)
- OrderServiceMDB uses standard @MessageDriven pattern (simpler conversion)
- WebLogic ApplicationLifecycleListener needs conversion to Quarkus events
- Manual Flyway @Singleton @Startup bean replaced by Quarkus extension
- All @Stateless EJBs become @ApplicationScoped CDI beans
- Estimated 4 complex (⚠️) steps out of 32 total steps