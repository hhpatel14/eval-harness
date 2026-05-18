# PLAN.md

## Goal
Migrate the Coolstore monolith from Java EE 7 (WebLogic/JBoss) to Quarkus 3, converting EJB services to CDI beans, JMS messaging to SmallRye Reactive Messaging, and removing all WebLogic/app-server dependencies.
- Reference used: javaee-quarkus.md

## Project Summary
- Type: Maven (WAR → JAR)
- Files affected: 30 (27 app source + 3 weblogic stubs to delete + pom.xml + config files)
- Estimated complexity: High
- Hardest steps:
  1. OrderServiceMDB.java — MDB → @Incoming reactive messaging
  2. InventoryNotificationMDB.java — MDB → @Incoming reactive messaging
  3. ShoppingCartOrderProcessor.java — JMS sender → Emitter
  4. StartupListener.java — WebLogic lifecycle → Quarkus lifecycle events

## Steps

### Step 1: Migrate build configuration (pom.xml)
- File: pom.xml
- Action: MODIFY
- What to do:
    - Change `<packaging>war</packaging>` → `<packaging>jar</packaging>`
    - Remove `javaee-web-api` dependency
    - Remove `javaee-api` dependency
    - Remove `jboss-jms-api_2.0_spec` dependency
    - Remove `jboss-rmi-api_1.0_spec` dependency
    - Remove `audit-logging-library` system-scoped dependency (or replace with a proper Maven dependency if available)
    - Remove `maven-war-plugin`
    - Update `maven-compiler-plugin` source/target from `1.8` to `17`
    - Add `<properties>` entry: `<quarkus.platform.version>3.8.4</quarkus.platform.version>`
    - Add `<dependencyManagement>` with Quarkus BOM:
      ```xml
      <dependencyManagement>
        <dependencies>
          <dependency>
            <groupId>io.quarkus.platform</groupId>
            <artifactId>quarkus-bom</artifactId>
            <version>${quarkus.platform.version}</version>
            <type>pom</type>
            <scope>import</scope>
          </dependency>
        </dependencies>
      </dependencyManagement>
      ```
    - Add Quarkus dependencies:
      - `io.quarkus:quarkus-arc` (CDI)
      - `io.quarkus:quarkus-rest-jackson` (JAX-RS + JSON)
      - `io.quarkus:quarkus-hibernate-orm` (JPA)
      - `io.quarkus:quarkus-jdbc-postgresql` (PostgreSQL driver)
      - `io.quarkus:quarkus-jdbc-h2` (dev/test, scope=test)
      - `io.quarkus:quarkus-flyway` (DB migrations)
      - `io.quarkus:quarkus-smallrye-reactive-messaging-amqp` (replaces JMS/MDB)
    - Add build plugin: `io.quarkus.platform:quarkus-maven-plugin:${quarkus.platform.version}`
    - Update Flyway dependency from `4.1.2` to version managed by Quarkus BOM (remove explicit version)
- Why: Quarkus uses JAR packaging, its own BOM, and specific extensions instead of Java EE APIs
- Depends on: none
- Verify: `mvn validate` passes without errors

### Step 2: Create application.properties
- File: src/main/resources/application.properties
- Action: CREATE
- What to do:
    - Add datasource configuration:
      ```properties
      quarkus.datasource.db-kind=postgresql
      quarkus.datasource.jdbc.url=jdbc:postgresql://localhost:5432/coolstore
      quarkus.datasource.username=${DB_USER:coolstore}
      quarkus.datasource.password=${DB_PASS:coolstore}
      quarkus.hibernate-orm.database.generation=none
      quarkus.flyway.migrate-at-start=true
      quarkus.flyway.locations=classpath:db/migration
      ```
    - Add reactive messaging channels for MDB replacements:
      ```properties
      # Outgoing: ShoppingCartOrderProcessor sends to topic/orders
      mp.messaging.outgoing.orders-outgoing.connector=smallrye-amqp
      mp.messaging.outgoing.orders-outgoing.address=topic/orders

      # Incoming: OrderServiceMDB and InventoryNotificationMDB both consume from topic/orders
      mp.messaging.incoming.orders-incoming.connector=smallrye-amqp
      mp.messaging.incoming.orders-incoming.address=topic/orders

      # Dev profile: use in-memory connector (no broker needed)
      %dev.mp.messaging.outgoing.orders-outgoing.connector=smallrye-in-memory
      %dev.mp.messaging.incoming.orders-incoming.connector=smallrye-in-memory
      ```
- Why: Replaces persistence.xml datasource config and provides messaging channel config
- Depends on: Step 1
- Verify: File exists at src/main/resources/application.properties

### Step 3: Migrate Producers utility
- File: src/main/java/com/redhat/coolstore/utils/Producers.java
- Action: MODIFY
- What to do:
    - Replace `javax.enterprise.*` → `jakarta.enterprise.*`
    - Replace `javax.inject.*` → `jakarta.inject.*`
    - Replace any `javax.annotation.*` → `jakarta.annotation.*`
- Why: Jakarta namespace migration for CDI producer methods
- Depends on: Step 1
- Verify: No `javax.` imports remain

### Step 4: Migrate Transformers utility
- File: src/main/java/com/redhat/coolstore/utils/Transformers.java
- Action: MODIFY
- What to do:
    - Replace `javax.json.*` → `jakarta.json.*`
    - Replace `javax.enterprise.*` → `jakarta.enterprise.*`
    - Replace `javax.inject.*` → `jakarta.inject.*`
    - Replace any `javax.ws.rs.*` → `jakarta.ws.rs.*`
- Why: Jakarta namespace migration
- Depends on: Step 1
- Verify: No `javax.` imports remain

### Step 5: ⚠️ COMPLEX — Migrate StartupListener (WebLogic lifecycle)
- File: src/main/java/com/redhat/coolstore/utils/StartupListener.java
- Action: MODIFY
- What to do:
    - BEFORE: Class extends `weblogic.application.ApplicationLifecycleListener`, uses
      `ApplicationLifecycleEvent`, `@Inject Logger log`, and overrides `postStart()` / `preStop()`
    - AFTER: Quarkus lifecycle events with CDI observers
    - Remove: `import weblogic.application.ApplicationLifecycleEvent;`
    - Remove: `import weblogic.application.ApplicationLifecycleListener;`
    - Remove: `extends ApplicationLifecycleListener`
    - Remove: `@Override` on both methods
    - Add: `import io.quarkus.runtime.StartupEvent;`
    - Add: `import io.quarkus.runtime.ShutdownEvent;`
    - Add: `import jakarta.enterprise.context.ApplicationScoped;`
    - Add: `import jakarta.enterprise.event.Observes;`
    - Add: `@ApplicationScoped` class annotation
    - Replace: `import javax.inject.Inject` → `import jakarta.inject.Inject`
    - Replace: `public void postStart(ApplicationLifecycleEvent evt)` → `void onStart(@Observes StartupEvent ev)`
    - Replace: `public void preStop(ApplicationLifecycleEvent evt)` → `void onStop(@Observes ShutdownEvent ev)`
    - Keep: `@Inject Logger log` (already uses `java.util.logging.Logger` — that's fine)
- Why: WebLogic lifecycle API does not exist in Quarkus; must use CDI lifecycle events
- Depends on: Step 1
- Verify: No `weblogic.*` imports remain; no `javax.*` imports remain; class compiles with Quarkus

### Step 6: Migrate DataBaseMigrationStartup — DELETE
- File: src/main/java/com/redhat/coolstore/utils/DataBaseMigrationStartup.java
- Action: DELETE
- What to do: Delete this file entirely. It is a `@Singleton @Startup` EJB that manually
  calls `Flyway.setDataSource()` / `flyway.baseline()` / `flyway.migrate()` using a
  `@Resource(mappedName = "java:jboss/datasources/CoolstoreDS")` DataSource.
  Quarkus handles all of this automatically with `quarkus.flyway.migrate-at-start=true`
  configured in application.properties (Step 2).
- Why: Quarkus Flyway extension auto-runs migrations at startup; this manual EJB startup bean is redundant
- Depends on: Step 2
- Verify: File no longer exists; Flyway still runs via application.properties config

### Step 7: Migrate Resources (persistence producer)
- File: src/main/java/com/redhat/coolstore/persistence/Resources.java
- Action: MODIFY
- What to do:
    - Replace `javax.persistence.*` → `jakarta.persistence.*`
    - Replace `javax.enterprise.*` → `jakarta.enterprise.*`
    - Replace `javax.inject.*` → `jakarta.inject.*`
- Why: Jakarta namespace migration for EntityManager producer
- Depends on: Step 1
- Verify: No `javax.` imports remain

### Step 8: Migrate CatalogItemEntity model
- File: src/main/java/com/redhat/coolstore/model/CatalogItemEntity.java
- Action: MODIFY
- What to do: Replace `javax.persistence.*` → `jakarta.persistence.*`
- Why: Jakarta namespace for JPA entities
- Depends on: Step 1
- Verify: No `javax.` imports remain

### Step 9: Migrate InventoryEntity model
- File: src/main/java/com/redhat/coolstore/model/InventoryEntity.java
- Action: MODIFY
- What to do: Replace `javax.persistence.*` → `jakarta.persistence.*`
- Why: Jakarta namespace for JPA entities
- Depends on: Step 1
- Verify: No `javax.` imports remain

### Step 10: Migrate Order model
- File: src/main/java/com/redhat/coolstore/model/Order.java
- Action: MODIFY
- What to do:
    - Replace `javax.persistence.*` → `jakarta.persistence.*`
    - Replace any `javax.xml.bind.*` → `jakarta.xml.bind.*` if present
- Why: Jakarta namespace for JPA entities
- Depends on: Step 1
- Verify: No `javax.` imports remain

### Step 11: Migrate OrderItem model
- File: src/main/java/com/redhat/coolstore/model/OrderItem.java
- Action: MODIFY
- What to do: Replace `javax.persistence.*` → `jakarta.persistence.*`
- Why: Jakarta namespace for JPA entities
- Depends on: Step 1
- Verify: No `javax.` imports remain

### Step 12: Migrate Product model
- File: src/main/java/com/redhat/coolstore/model/Product.java
- Action: MODIFY
- What to do:
    - Replace `javax.persistence.*` → `jakarta.persistence.*` if annotated
    - Replace any other `javax.*` → `jakarta.*`
- Why: Jakarta namespace migration
- Depends on: Step 1
- Verify: No `javax.` imports remain

### Step 13: Migrate Promotion model
- File: src/main/java/com/redhat/coolstore/model/Promotion.java
- Action: MODIFY
- What to do: Replace `javax.persistence.*` → `jakarta.persistence.*` if annotated
- Why: Jakarta namespace migration
- Depends on: Step 1
- Verify: No `javax.` imports remain

### Step 14: Migrate ShoppingCart model
- File: src/main/java/com/redhat/coolstore/model/ShoppingCart.java
- Action: MODIFY
- What to do:
    - Replace `javax.persistence.*` → `jakarta.persistence.*` if annotated
    - Replace any `javax.xml.bind.*` → `jakarta.xml.bind.*` if present
- Why: Jakarta namespace migration
- Depends on: Step 1
- Verify: No `javax.` imports remain

### Step 15: Migrate ShoppingCartItem model
- File: src/main/java/com/redhat/coolstore/model/ShoppingCartItem.java
- Action: MODIFY
- What to do: Replace `javax.persistence.*` → `jakarta.persistence.*` if annotated
- Why: Jakarta namespace migration
- Depends on: Step 1
- Verify: No `javax.` imports remain

### Step 16: Migrate CatalogService
- File: src/main/java/com/redhat/coolstore/service/CatalogService.java
- Action: MODIFY
- What to do:
    - Replace `javax.ejb.Stateless` → remove import
    - Replace `@Stateless` → `@ApplicationScoped` (add `import jakarta.enterprise.context.ApplicationScoped;`)
    - Replace `@EJB` → `@Inject` (add `import jakarta.inject.Inject;`)
    - Replace `javax.persistence.*` → `jakarta.persistence.*`
    - Replace any remaining `javax.*` → `jakarta.*`
- Why: EJB → CDI bean conversion
- Depends on: Steps 8, 9
- Verify: No `javax.` imports remain; no EJB annotations

### Step 17: Migrate ProductService
- File: src/main/java/com/redhat/coolstore/service/ProductService.java
- Action: MODIFY
- What to do:
    - Replace `@Stateless` → `@ApplicationScoped`
    - Replace `@EJB` → `@Inject`
    - Replace all `javax.*` → `jakarta.*`
    - Remove `javax.ejb.*` imports
- Why: EJB → CDI bean conversion
- Depends on: Step 12
- Verify: No `javax.` or EJB imports remain

### Step 18: Migrate PromoService
- File: src/main/java/com/redhat/coolstore/service/PromoService.java
- Action: MODIFY
- What to do:
    - Replace `@Stateless` → `@ApplicationScoped`
    - Replace all `javax.*` → `jakarta.*`
    - Remove `javax.ejb.*` imports
- Why: EJB → CDI bean conversion
- Depends on: Step 13
- Verify: No `javax.` or EJB imports remain

### Step 19: Migrate OrderService
- File: src/main/java/com/redhat/coolstore/service/OrderService.java
- Action: MODIFY
- What to do:
    - Replace `@Stateless` → `@ApplicationScoped`
    - Replace `@EJB` → `@Inject`
    - Replace all `javax.*` → `jakarta.*`
    - Remove `javax.ejb.*` imports
- Why: EJB → CDI bean conversion
- Depends on: Steps 10, 11
- Verify: No `javax.` or EJB imports remain

### Step 20: Migrate ShippingService
- File: src/main/java/com/redhat/coolstore/service/ShippingService.java
- Action: MODIFY
- What to do:
    - Replace `@Stateless` → `@ApplicationScoped`
    - Remove any `@Remote` / `@Local` annotations
    - Replace all `javax.*` → `jakarta.*`
    - Remove `javax.ejb.*` imports
- Why: EJB → CDI bean conversion; remote interfaces not applicable in Quarkus
- Depends on: Step 1
- Verify: No `javax.` or EJB imports remain

### Step 21: Remove ShippingServiceRemote interface
- File: src/main/java/com/redhat/coolstore/service/ShippingServiceRemote.java
- Action: DELETE
- What to do: Delete this file entirely — Remote EJB interfaces are not used in Quarkus
- Why: Quarkus uses direct CDI injection; remote interfaces are a Java EE/app-server concept
- Depends on: Step 20
- Verify: File no longer exists; no compile errors referencing it

### Step 22: Migrate ShoppingCartService
- File: src/main/java/com/redhat/coolstore/service/ShoppingCartService.java
- Action: MODIFY
- What to do:
    - Replace `@Stateless` → `@ApplicationScoped`
    - Replace `@EJB` → `@Inject`
    - Replace all `javax.*` → `jakarta.*`
    - Remove `javax.ejb.*` imports
- Why: EJB → CDI bean conversion
- Depends on: Steps 14, 15, 18, 20
- Verify: No `javax.` or EJB imports remain

### Step 23: ⚠️ COMPLEX — Migrate ShoppingCartOrderProcessor (JMS sender)
- File: src/main/java/com/redhat/coolstore/service/ShoppingCartOrderProcessor.java
- Action: MODIFY
- What to do:
    - BEFORE: `@Stateless` class with `@Inject JMSContext context`, `@Resource(lookup = "java:/topic/orders") Topic ordersTopic`,
      sends via `context.createProducer().send(ordersTopic, Transformers.shoppingCartToJson(cart))`
    - AFTER: CDI bean with SmallRye Emitter
    - Remove: `import javax.ejb.Stateless;`
    - Remove: `import javax.annotation.Resource;`
    - Remove: `import javax.jms.JMSContext;`
    - Remove: `import javax.jms.Topic;`
    - Remove: `@Resource(lookup = "java:/topic/orders") private Topic ordersTopic;`
    - Remove: `@Inject private transient JMSContext context;`
    - Add: `import jakarta.enterprise.context.ApplicationScoped;`
    - Add: `import org.eclipse.microprofile.reactive.messaging.Channel;`
    - Add: `import org.eclipse.microprofile.reactive.messaging.Emitter;`
    - Replace: `import javax.inject.Inject` → `import jakarta.inject.Inject`
    - Add: `@ApplicationScoped` class annotation (replacing `@Stateless`)
    - Add field: `@Inject @Channel("orders-outgoing") Emitter<String> emitter;`
    - Replace in `process()` method:
      `context.createProducer().send(ordersTopic, Transformers.shoppingCartToJson(cart))`
      → `emitter.send(Transformers.shoppingCartToJson(cart));`
    - Keep: `@Inject Logger log;` (already uses `java.util.logging.Logger`)
- Why: JMS API is not available in Quarkus; SmallRye Reactive Messaging replaces it
- Depends on: Steps 2, 14
- Verify: No `javax.jms.*` imports; channel name `orders-outgoing` matches application.properties

### Step 24: ⚠️ COMPLEX — Migrate OrderServiceMDB (Message-Driven Bean)
- File: src/main/java/com/redhat/coolstore/service/OrderServiceMDB.java
- Action: MODIFY
- What to do:
    - BEFORE: `@MessageDriven(name = "OrderServiceMDB", activationConfig = {...destinationLookup = "topic/orders", destinationType = "javax.jms.Topic"...})`
      class `implements MessageListener`. Method `onMessage(Message rcvMessage)` casts to `TextMessage`,
      calls `msg.getBody(String.class)`, then `Transformers.jsonToOrder(orderStr)`, saves order via
      `orderService.save(order)` and updates inventory via `catalogService.updateInventoryItems(...)`.
      Uses `@Inject OrderService orderService` and `@Inject CatalogService catalogService`.
    - AFTER: `@ApplicationScoped` + `@Incoming("orders-incoming")`
    - Remove: `import javax.ejb.ActivationConfigProperty;`
    - Remove: `import javax.ejb.MessageDriven;`
    - Remove: `import javax.jms.JMSException;`
    - Remove: `import javax.jms.Message;`
    - Remove: `import javax.jms.MessageListener;`
    - Remove: `import javax.jms.TextMessage;`
    - Remove: `implements MessageListener`
    - Remove: entire `@MessageDriven(...)` annotation block
    - Add: `import jakarta.enterprise.context.ApplicationScoped;`
    - Add: `import org.eclipse.microprofile.reactive.messaging.Incoming;`
    - Replace: `import javax.inject.Inject` → `import jakarta.inject.Inject`
    - Add: `@ApplicationScoped` class annotation
    - Replace method signature:
      ```java
      // BEFORE
      @Override
      public void onMessage(Message rcvMessage) {
          TextMessage msg = null;
          try {
              if (rcvMessage instanceof TextMessage) {
                  msg = (TextMessage) rcvMessage;
                  String orderStr = msg.getBody(String.class);
                  // ...processing...
              }
          } catch (JMSException e) { throw new RuntimeException(e); }
      }
      // AFTER
      @Incoming("orders-incoming")
      public void onMessage(String orderStr) {
          System.out.println("Received order: " + orderStr);
          Order order = Transformers.jsonToOrder(orderStr);
          System.out.println("Order object is " + order);
          orderService.save(order);
          order.getItemList().forEach(orderItem -> {
              catalogService.updateInventoryItems(orderItem.getProductId(), orderItem.getQuantity());
          });
      }
      ```
    - The body arrives as a String directly — no TextMessage casting or JMSException handling needed
- Why: MDBs are an EJB concept; Quarkus uses reactive messaging with @Incoming
- Depends on: Steps 2, 16, 19
- Verify: No `javax.jms.*` or `javax.ejb.*` imports; channel `orders-incoming` matches application.properties

### Step 25: ⚠️ COMPLEX — Migrate InventoryNotificationMDB (WebLogic JNDI JMS listener)
- File: src/main/java/com/redhat/coolstore/service/InventoryNotificationMDB.java
- Action: MODIFY
- What to do:
    - BEFORE: This is NOT a standard `@MessageDriven` — it is a raw JMS listener using WebLogic JNDI.
      It `implements MessageListener` with manual JNDI lookup via `weblogic.jndi.WLInitialContextFactory`,
      `PortableRemoteObject.narrow()`, `TopicConnectionFactory`, `TopicConnection`, `TopicSession`,
      `TopicSubscriber`, and connects to `t3://localhost:7001`. Has `init()` and `close()` lifecycle
      methods. The `onMessage()` processes orders and checks inventory thresholds.
    - AFTER: `@ApplicationScoped` + `@Incoming("orders-incoming")` (subscribes to same topic as OrderServiceMDB)
    - Remove ALL of these imports:
      - `javax.jms.*` (JMSException, Message, MessageListener, TextMessage, TopicConnection, etc.)
      - `javax.naming.Context`, `javax.naming.InitialContext`, `javax.naming.NamingException`
      - `javax.rmi.PortableRemoteObject`
      - `java.util.Hashtable`
    - Remove: `implements MessageListener`
    - Remove: ALL JNDI constants (`JNDI_FACTORY`, `JMS_FACTORY`, `TOPIC`)
    - Remove: ALL JMS connection fields (`tcon`, `tsession`, `tsubscriber`)
    - Remove: entire `init()` method (JNDI/JMS connection setup)
    - Remove: entire `close()` method (JMS connection cleanup)
    - Remove: entire `getInitialContext()` method (WebLogic JNDI context)
    - Add: `import jakarta.enterprise.context.ApplicationScoped;`
    - Add: `import jakarta.inject.Inject;`
    - Add: `import org.eclipse.microprofile.reactive.messaging.Incoming;`
    - Replace: `import javax.inject.Inject` → `import jakarta.inject.Inject`
    - Add: `@ApplicationScoped` class annotation
    - Replace method:
      ```java
      // BEFORE — complex JMS message handling
      public void onMessage(Message rcvMessage) {
          TextMessage msg;
          try {
              if (rcvMessage instanceof TextMessage) {
                  msg = (TextMessage) rcvMessage;
                  String orderStr = msg.getBody(String.class);
                  // ...inventory threshold logic...
              }
          } catch (JMSException jmse) { ... }
      }
      // AFTER — clean reactive consumer
      @Incoming("orders-incoming")
      public void onMessage(String orderStr) {
          System.out.println("received message inventory");
          Order order = Transformers.jsonToOrder(orderStr);
          order.getItemList().forEach(orderItem -> {
              int old_quantity = catalogService.getCatalogItemById(orderItem.getProductId()).getInventory().getQuantity();
              int new_quantity = old_quantity - orderItem.getQuantity();
              if (new_quantity < LOW_THRESHOLD) {
                  System.out.println("Inventory for item " + orderItem.getProductId() + " is below threshold (" + LOW_THRESHOLD + "), contact supplier!");
              } else {
                  orderItem.setQuantity(new_quantity);
              }
          });
      }
      ```
    - Keep: `LOW_THRESHOLD` constant, `@Inject CatalogService catalogService`
    - NOTE: Both this and OrderServiceMDB subscribe to the same `topic/orders`. In Quarkus
      reactive messaging, use the same channel name `orders-incoming` — both beans will receive
      the message (broadcast behavior). Alternatively, create a separate channel with the same
      address if independent consumption is needed.
- Why: Raw WebLogic JNDI JMS code must be completely replaced; no part of it is reusable in Quarkus
- Depends on: Steps 2, 8, 16
- Verify: No `javax.jms.*`, `javax.naming.*`, `javax.rmi.*`, or `weblogic.*` references; channel matches application.properties

### Step 26: Migrate RestApplication
- File: src/main/java/com/redhat/coolstore/rest/RestApplication.java
- Action: MODIFY
- What to do:
    - Replace `javax.ws.rs.ApplicationPath` → `jakarta.ws.rs.ApplicationPath`
    - Replace `javax.ws.rs.core.Application` → `jakarta.ws.rs.core.Application`
- Why: Jakarta namespace for JAX-RS application class
- Depends on: Step 1
- Verify: No `javax.` imports remain

### Step 27: Migrate CartEndpoint (HIGH RISK — god node)
- File: src/main/java/com/redhat/coolstore/rest/CartEndpoint.java
- Action: MODIFY
- What to do:
    - Replace `javax.ws.rs.*` → `jakarta.ws.rs.*`
    - Replace `@EJB` → `@Inject`
    - Replace `javax.inject.*` → `jakarta.inject.*`
    - Replace `javax.enterprise.*` → `jakarta.enterprise.*`
    - Remove `javax.ejb.*` imports
- Why: JAX-RS endpoint with many connections; careful namespace migration
- Depends on: Steps 22, 14
- Verify: No `javax.` imports remain

### Step 28: Migrate OrderEndpoint (HIGH RISK — god node)
- File: src/main/java/com/redhat/coolstore/rest/OrderEndpoint.java
- Action: MODIFY
- What to do:
    - Replace `javax.ws.rs.*` → `jakarta.ws.rs.*`
    - Replace `@EJB` → `@Inject`
    - Replace `javax.inject.*` → `jakarta.inject.*`
    - Replace `javax.enterprise.*` → `jakarta.enterprise.*`
    - Remove `javax.ejb.*` imports
- Why: JAX-RS endpoint with many connections; careful namespace migration
- Depends on: Steps 19, 10
- Verify: No `javax.` imports remain

### Step 29: Migrate ProductEndpoint
- File: src/main/java/com/redhat/coolstore/rest/ProductEndpoint.java
- Action: MODIFY
- What to do:
    - Replace `javax.ws.rs.*` → `jakarta.ws.rs.*`
    - Replace `@EJB` → `@Inject`
    - Replace `javax.inject.*` → `jakarta.inject.*`
    - Replace `javax.enterprise.*` → `jakarta.enterprise.*`
    - Remove `javax.ejb.*` imports
- Why: Jakarta namespace migration for JAX-RS endpoint
- Depends on: Steps 16, 17
- Verify: No `javax.` imports remain

### Step 30: Delete persistence.xml
- File: src/main/resources/META-INF/persistence.xml
- Action: DELETE
- What to do: Delete this file — datasource config is now in application.properties
- Why: Quarkus configures JPA/Hibernate via application.properties, not persistence.xml
- Depends on: Step 2
- Verify: File no longer exists

### Step 31: Delete beans.xml
- File: src/main/webapp/WEB-INF/beans.xml
- Action: DELETE
- What to do: Delete this file — Quarkus enables CDI automatically
- Why: Not needed in Quarkus; CDI bean discovery is automatic
- Depends on: none
- Verify: File no longer exists

### Step 32: Delete web.xml
- File: src/main/webapp/WEB-INF/web.xml
- Action: DELETE
- What to do: Delete this file — Quarkus does not use web.xml
- Why: Quarkus uses application.properties for servlet/web config
- Depends on: none
- Verify: File no longer exists

### Step 33: Delete WebLogic stub classes
- File: src/main/java/weblogic/application/ApplicationLifecycleEvent.java
- Action: DELETE
- What to do: Delete this WebLogic stub class
- Why: WebLogic APIs are no longer needed after Quarkus migration
- Depends on: Step 5
- Verify: File no longer exists

### Step 34: Delete WebLogic stub — ApplicationLifecycleListener
- File: src/main/java/weblogic/application/ApplicationLifecycleListener.java
- Action: DELETE
- What to do: Delete this WebLogic stub class
- Why: WebLogic APIs are no longer needed after Quarkus migration
- Depends on: Step 5
- Verify: File no longer exists

### Step 35: Delete WebLogic stub — NonCatalogLogger
- File: src/main/java/weblogic/i18n/logging/NonCatalogLogger.java
- Action: DELETE
- What to do: Delete this WebLogic stub class
- Why: WebLogic logging API replaced by standard `org.jboss.logging.Logger` or `java.util.logging`
- Depends on: Step 5
- Verify: File no longer exists; entire `src/main/java/weblogic/` directory can be removed

## Verification

```bash
# Full compile check — should pass with zero errors
mvn clean compile 2>&1 | tail -30

# Verify no javax imports remain
grep -r "javax\." src/main/java/ --include="*.java" | grep -v "javax.sql" | grep -v "javax.crypto" | grep -v "javax.net"

# Verify no weblogic imports remain
grep -r "weblogic\." src/main/java/ --include="*.java"

# Start in dev mode
mvn quarkus:dev
```

## Notes
- The `audit-logging-library` system-scoped dependency may need a proper Maven repository or be converted to a local install. Verify after build.
- ShippingServiceRemote.java is deleted — verify no other file references this interface (update any `implements ShippingServiceRemote` in ShippingService.java).
- Channel names in application.properties must match `@Incoming`/`@Channel` annotations exactly.
- The webapp/ directory with bower_components is frontend code — it remains unchanged in this migration.
- Flyway migration SQL files should already be in `src/main/resources/db/migration/` — verify they exist.
