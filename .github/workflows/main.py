#!/usr/bin/env python3
"""
Production Latency Debug Starter Kit
A simple CLI tool to detect common production latency issues from Prometheus metrics.
"""

import argparse
import re
import sys
from collections import defaultdict
from typing import Dict, List, Optional, Tuple


class MetricsParser:
    """Parse Prometheus-style metrics and extract relevant data."""
    
    def __init__(self, metrics_text: str):
        self.metrics_text = metrics_text
        self.metrics = {}
        self.histograms = defaultdict(list)
        self._parse()
    
    def _parse(self):
        """Parse Prometheus text format metrics."""
        lines = self.metrics_text.strip().split('\n')
        
        for line in lines:
            line = line.strip()
            if not line or line.startswith('#'):
                continue
            
            # Match metric_name{labels} value or metric_name value
            # Prometheus metric names can contain dots, underscores, colons, and alphanumeric
            match = re.match(r'^([a-zA-Z_:][a-zA-Z0-9_.:]*)(?:\{([^}]*)\})?\s+([0-9.+-eE]+)', line)
            if match:
                metric_name = match.group(1)
                labels = match.group(2) or ''
                value = float(match.group(3))
                
                # Store histogram buckets
                if '_bucket' in metric_name:
                    self.histograms[metric_name].append((labels, value))
                else:
                    key = f"{metric_name}{{{labels}}}" if labels else metric_name
                    self.metrics[key] = value
    
    def get_metric(self, pattern: str) -> Optional[float]:
        """Get a metric value by pattern matching."""
        for key, value in self.metrics.items():
            if pattern in key:
                return value
        return None
    
    def get_histogram(self, metric_name: str) -> List[Tuple[str, float]]:
        """Get histogram buckets for a metric."""
        return self.histograms.get(metric_name, [])


class LatencyAnalyzer:
    """Analyze latency distributions from histogram metrics."""
    
    @staticmethod
    def calculate_percentiles(buckets: List[Tuple[str, float]], total_count: float) -> Dict[str, float]:
        """
        Calculate percentiles from histogram buckets.
        Returns p50, p95, p99 in seconds.
        """
        if not buckets or total_count == 0:
            return {}
        
        # Sort buckets by le (less than or equal) value
        sorted_buckets = []
        for labels, count in buckets:
            le_match = re.search(r'le="([0-9.]+)"', labels)
            if le_match:
                le_value = float(le_match.group(1))
                sorted_buckets.append((le_value, count))
        
        sorted_buckets.sort(key=lambda x: x[0])
        
        percentiles = {}
        target_percentiles = {'p50': 0.5, 'p95': 0.95, 'p99': 0.99}
        
        for p_name, p_value in target_percentiles.items():
            target_count = total_count * p_value
            for le_value, count in sorted_buckets:
                if count >= target_count:
                    percentiles[p_name] = le_value
                    break
        
        return percentiles
    
    @staticmethod
    def find_latency_histogram(parser: MetricsParser) -> Optional[str]:
        """Find HTTP request latency histogram metric."""
        patterns = [
            'http_request_duration_seconds_bucket',
            'http_server_request_duration_seconds_bucket',
            'http_client_request_duration_seconds_bucket',
            'request_duration_seconds_bucket',
        ]
        
        for pattern in patterns:
            for metric_name in parser.histograms.keys():
                if pattern in metric_name:
                    return metric_name
        
        return None


class IssueDetector:
    """Detect common production latency issues."""
    
    def __init__(self, parser: MetricsParser):
        self.parser = parser
        self.issues = []
    
    def check_thread_pool(self):
        """Check thread pool saturation."""
        patterns = [
            ('tomcat.threads.busy', 'tomcat.threads.max'),
            ('thread_pool_active', 'thread_pool_max'),
            ('threads_active', 'threads_max'),
        ]
        
        for busy_pattern, max_pattern in patterns:
            busy = self.parser.get_metric(busy_pattern)
            max_threads = self.parser.get_metric(max_pattern)
            
            if busy is not None and max_threads is not None and max_threads > 0:
                usage_percent = (busy / max_threads) * 100
                if usage_percent > 80:
                    self.issues.append({
                        'type': 'thread_pool_saturation',
                        'severity': 'high' if usage_percent > 90 else 'medium',
                        'metric': f"{busy_pattern}/{max_pattern}",
                        'usage': f"{usage_percent:.1f}%",
                        'explanation': (
                            f"Thread pool is {usage_percent:.1f}% utilized ({busy}/{max_threads} threads). "
                            f"This means your application is running out of worker threads, causing requests "
                            f"to queue up and increasing latency. Consider increasing thread pool size or "
                            f"optimizing request processing time."
                        )
                    })
                return
    
    def check_connection_pool(self):
        """Check database connection pool saturation."""
        patterns = [
            ('hikaricp.connections.active', 'hikaricp.connections.max'),
            ('db_connections_active', 'db_connections_max'),
            ('connection_pool_active', 'connection_pool_max'),
            ('datasource_active', 'datasource_max'),
        ]
        
        for active_pattern, max_pattern in patterns:
            active = self.parser.get_metric(active_pattern)
            max_connections = self.parser.get_metric(max_pattern)
            
            if active is not None and max_connections is not None and max_connections > 0:
                usage_percent = (active / max_connections) * 100
                if usage_percent > 80:
                    self.issues.append({
                        'type': 'connection_pool_saturation',
                        'severity': 'high' if usage_percent > 90 else 'medium',
                        'metric': f"{active_pattern}/{max_pattern}",
                        'usage': f"{usage_percent:.1f}%",
                        'explanation': (
                            f"Database connection pool is {usage_percent:.1f}% utilized ({active}/{max_connections} connections). "
                            f"High connection pool usage indicates your application is waiting for available database "
                            f"connections, which directly increases request latency. Check for slow queries, connection leaks, "
                            f"or consider increasing pool size."
                        )
                    })
                return
    
    def check_latency_distribution(self):
        """Check for latency distribution anomalies."""
        analyzer = LatencyAnalyzer()
        histogram_name = analyzer.find_latency_histogram(self.parser)
        
        if not histogram_name:
            return
        
        buckets = self.parser.get_histogram(histogram_name)
        if not buckets:
            return
        
        # Find total count (usually +Inf bucket or sum)
        total_count = 0
        for labels, count in buckets:
            if 'le="+Inf"' in labels or 'le="+inf"' in labels.lower():
                total_count = count
                break
        
        if total_count == 0:
            # Try to find sum metric
            sum_pattern = histogram_name.replace('_bucket', '_sum')
            count_pattern = histogram_name.replace('_bucket', '_count')
            for key in self.parser.metrics.keys():
                if sum_pattern in key:
                    # Estimate from sum if available
                    pass
                if count_pattern in key:
                    total_count = self.parser.metrics[key]
        
        if total_count == 0:
            return
        
        percentiles = analyzer.calculate_percentiles(buckets, total_count)
        
        if 'p50' in percentiles and 'p95' in percentiles:
            p50 = percentiles['p50']
            p95 = percentiles['p95']
            
            if p95 > 3 * p50:
                self.issues.append({
                    'type': 'latency_tail',
                    'severity': 'high',
                    'metric': histogram_name,
                    'p50': f"{p50*1000:.1f}ms",
                    'p95': f"{p95*1000:.1f}ms",
                    'ratio': f"{p95/p50:.1f}x",
                    'explanation': (
                        f"Latency tail detected: p95 ({p95*1000:.1f}ms) is {p95/p50:.1f}x higher than p50 ({p50*1000:.1f}ms). "
                        f"This indicates that a small percentage of requests are experiencing significantly higher latency. "
                        f"Common causes: slow database queries, external API calls, garbage collection pauses, or resource "
                        f"contention. Investigate what makes these requests different from the majority."
                    )
                })
    
    def detect_all(self):
        """Run all checks."""
        self.check_thread_pool()
        self.check_connection_pool()
        self.check_latency_distribution()
        return self.issues


def print_results(issues: List[Dict]):
    """Print detected issues in a human-readable format."""
    if not issues:
        print("✅ No issues detected! Your metrics look healthy.")
        print("\nNote: This tool checks for common patterns. Make sure your metrics file")
        print("contains the expected metric names (thread pools, connection pools, latency histograms).")
        return
    
    print(f"\n⚠️  Found {len(issues)} potential issue(s):\n")
    print("=" * 80)
    
    for i, issue in enumerate(issues, 1):
        severity_emoji = "🔴" if issue['severity'] == 'high' else "🟡"
        print(f"\n{severity_emoji} Issue #{i}: {issue['type'].replace('_', ' ').title()}")
        print("-" * 80)
        
        if 'usage' in issue:
            print(f"Usage: {issue['usage']}")
        if 'p50' in issue:
            print(f"p50: {issue['p50']}, p95: {issue['p95']} (ratio: {issue['ratio']})")
        if 'metric' in issue:
            print(f"Metric: {issue['metric']}")
        
        print(f"\n💡 Explanation:")
        print(f"   {issue['explanation']}")
        print()
    
    print("=" * 80)
    print("\n💼 Next Steps:")
    print("   1. Verify these metrics in your monitoring dashboard")
    print("   2. Check application logs around the time these metrics were collected")
    print("   3. Review recent deployments or configuration changes")
    print("   4. Consider increasing resource limits or optimizing slow operations")


def main():
    parser = argparse.ArgumentParser(
        description='Production Latency Debug Starter Kit - Detect common latency issues from Prometheus metrics',
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog="""
Examples:
  python main.py sample_metrics.txt
  python main.py /path/to/your/metrics.txt
        """
    )
    parser.add_argument(
        'metrics_file',
        type=str,
        help='Path to Prometheus metrics file (text format)'
    )
    
    args = parser.parse_args()
    
    try:
        with open(args.metrics_file, 'r') as f:
            metrics_text = f.read()
    except FileNotFoundError:
        print(f"❌ Error: File '{args.metrics_file}' not found.", file=sys.stderr)
        sys.exit(1)
    except Exception as e:
        print(f"❌ Error reading file: {e}", file=sys.stderr)
        sys.exit(1)
    
    print("🔍 Analyzing metrics...\n")
    
    metrics_parser = MetricsParser(metrics_text)
    detector = IssueDetector(metrics_parser)
    issues = detector.detect_all()
    
    print_results(issues)


if __name__ == '__main__':
    main()

