package com.amitra.rediscount.config;

import com.codahale.metrics.Metric;
import com.codahale.metrics.MetricFilter;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.MetricSet;
import com.codahale.metrics.graphite.GraphiteReporter;
import com.codahale.metrics.graphite.GraphiteSender;
import com.codahale.metrics.graphite.GraphiteUDP;
import com.codahale.metrics.jvm.FileDescriptorRatioGauge;
import com.codahale.metrics.jvm.GarbageCollectorMetricSet;
import com.codahale.metrics.jvm.MemoryUsageGaugeSet;
import com.codahale.metrics.jvm.ThreadStatesGaugeSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.inject.Inject;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Configuration
public class MetricsConfig {

  private static final Logger log = LoggerFactory.getLogger(MetricsConfig.class);

  @Value("${monitoring.app.name}")
  private String appName;

  @Value("${monitoring.app.prefix}")
  private String appPrefix;

  @Value("${graphite.host}")
  private String graphiteHost;

  @Value("${graphite.port}")
  private Integer graphitePort;

  @Value("${graphite.config.duration}")
  private Integer graphiteDuration;


  private final MetricRegistry metricRegistry;

  @Inject
  public MetricsConfig(MetricRegistry metricRegistry) {
    this.metricRegistry = metricRegistry;
  }

  @Bean(destroyMethod = "stop")
  GraphiteReporter graphiteReporter() throws UnknownHostException {

    // add some JVM metrics (wrap in MetricSet to add better key prefixes)
    MetricSet jvmMetrics = () -> {

      Map<String, Metric> metrics = new HashMap<>();
      metrics.put("gc", new GarbageCollectorMetricSet());
      metrics.put("file-descriptors", new FileDescriptorRatioGauge());
      metrics.put("memory-usage", new MemoryUsageGaugeSet());
      metrics.put("threads", new ThreadStatesGaugeSet());
      return metrics;
    };

    metricRegistry.registerAll(jvmMetrics);


    // create and start reporter
    //use this for TCP
    //final GraphiteSender graphite = new Graphite(new InetSocketAddress(graphiteHost, graphitePort));
    //If you want UDP reporter change this out.
    final GraphiteSender graphite = new GraphiteUDP(new InetSocketAddress(graphiteHost, graphitePort));

    final String graphiteMetricsPrefix = getGraphitePrefix(appName,appPrefix);

    log.info("the graphite metrics prefix is {}",graphiteMetricsPrefix);

    final GraphiteReporter reporter = GraphiteReporter.forRegistry(metricRegistry)
        .prefixedWith(graphiteMetricsPrefix)
        .convertRatesTo(TimeUnit.MILLISECONDS).convertDurationsTo(TimeUnit.MILLISECONDS).filter(MetricFilter.ALL)
        .build(graphite);
    reporter.start(graphiteDuration, TimeUnit.MINUTES);
    return reporter;
  }

  protected String getGraphitePrefix(String appName,String appPrefix) throws UnknownHostException {
    String hostName=(InetAddress.getLocalHost().getHostName()).replaceAll("\\.","_");

    return (appPrefix.concat(appName.replaceAll("\\.","_"))
        .concat(".").concat(hostName));
  }
}
