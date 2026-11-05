#!/usr/bin/env ruby
# Read-only inventory; output is reproducible and does not claim semantic portability.
require 'json'
root = File.expand_path('../..', __dir__)
groups = {
  'java_io_zip' => /^(java\.io\.|java\.nio\.|java\.util\.zip\.)/,
  'xml' => /^(org\.w3c\.|org\.xml\.|javax\.xml\.)/,
  'json' => /^org\.json\./,
  'android' => /^(android\.|androidx\.)/,
  'numeric' => /^java\.math\./,
  'identity_collections' => /^java\.util\.(IdentityHashMap|Collections)/,
  'concurrency' => /^(java\.util\.concurrent\.|kotlinx\.coroutines\.)/,
  'time' => /^java\.(time\.|util\.(Date|Calendar|TimeZone))/,
  'security' => /^java\.(security\.|util\.UUID)/
}
files = Dir.glob(File.join(root, 'app/src/main/java/com/xnotes/{core,format}/**/*.kt')).sort
result = files.map do |file|
  imports = File.readlines(file).map { |line| line[/^import\s+([^\s;]+)/, 1] }.compact
  matches = groups.transform_values { |pattern| imports.grep(pattern) }.reject { |_, values| values.empty? }
  next if matches.empty?
  { file: file.delete_prefix(root + '/'), dependencies: matches }
end.compact
puts JSON.pretty_generate({ scanned_files: files.length, flagged_files: result.length, files: result })
