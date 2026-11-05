#!/usr/bin/env ruby
# Probe-only adaptation: production Android sources remain byte-for-byte untouched.
require 'fileutils'
root = File.expand_path('../..', __dir__)
out = File.join(root, 'tmp/kmp-format-probe/com/xnotes/format')
FileUtils.mkdir_p(out)
%w[JsonPull JsonWrite RawSamples].each do |name|
  source = File.read(File.join(root, "app/src/main/java/com/xnotes/format/#{name}.kt"))
  source = source.sub("import java.io.Reader", "")
                 .sub("import java.io.Writer", "")
                 .gsub('Math.round(', 'roundLikeJava(')
                 .gsub('sb.append(buf, pos, i - pos)', 'sb.appendRange(buf, pos, i)')
                 .sub('DoubleArray(23) { java.math.BigDecimal.TEN.pow(it).toDouble() }',
                      'doubleArrayOf(' + (0..22).map { |i| "1e#{i}" }.join(', ') + ')')
                 .sub('String.format(java.util.Locale.ROOT, "\\\\u%04x", c.code)',
                      '92.toChar().toString() + "u" + c.code.toString(16).padStart(4, \'0\')')
if name == 'JsonPull'
  source = source.sub('    fun peek(): Token', "    fun requireDocumentEnd() { skipWs(); require(!ensure()) { \"Trailing JSON data\" } }

    fun peek(): Token")
end
  abort "Unadapted platform call in #{name}" if source.match?(/java\.|Math\.round|String\.format/)
  File.write(File.join(out, "#{name}.kt"), source)
end
# JVM oracle uses untouched algorithms and the real Java I/O adapters.
oracle = File.join(root, 'tmp/kmp-format-oracle/com/xnotes/original')
FileUtils.mkdir_p(oracle)
%w[JsonPull JsonWrite].each do |name|
  source = File.read(File.join(root, "app/src/main/java/com/xnotes/format/#{name}.kt"))
  File.write(File.join(oracle, "#{name}.kt"), source.sub('package com.xnotes.format', 'package com.xnotes.original'))
end
geometry_dir = File.join(root, 'tmp/kmp-format-probe/com/xnotes/core/stroke')
FileUtils.mkdir_p(geometry_dir)
geometry = File.read(File.join(root, 'app/src/main/java/com/xnotes/core/stroke/StrokeGeometry.kt'))
File.write(File.join(geometry_dir, 'StrokeGeometry.kt'), geometry.sub('import com.xnotes.core.geometry.Pt', "import com.xnotes.core.geometry.Pt\nimport kotlin.concurrent.Volatile"))
