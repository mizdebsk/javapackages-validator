/*-
 * Copyright (c) 2020 Red Hat, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.fedoraproject.javapackages.validator;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;

import java.text.MessageFormat;

import org.apache.commons.compress.archivers.cpio.CpioArchiveEntry;
import org.apache.commons.compress.archivers.jar.JarArchiveInputStream;
import org.fedoraproject.javadeptools.rpm.RpmArchiveInputStream;
import org.fedoraproject.javadeptools.rpm.RpmInfo;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;

import org.fedoraproject.javapackages.validator.Validator.Test_result;

/**
 * @author Marián Konček
 */
public class Package_test
{
	static private Ansi_colors.Decorator color_decorator = new Ansi_colors.No_decorator();
	static final Ansi_colors.Decorator color_decorator()
	{
		return color_decorator;
	}
	
	static class Arguments
	{
		@Parameter(names = {"-h", "--help"}, help = true, description =
				"Display help.")
		boolean help = false;
		
		@Parameter(names = {"-o", "--output"}, description =
				"The file to write the output to. " +
				"If not provided then outputs to the standard output.")
		String output_file = null;
		
		@Parameter(names = {"-c", "--config"}, description =
				"The file to read the configuration from.")
		String config_file = null;
		
		@Parameter(names = {"-f", "--files"}, variableArity = true, description =
				"The list of .rpm files to validate.")
		ArrayList<String> test_files = new ArrayList<>();
		
		@Parameter(names = {"-i", "--input"}, description =
				"The file to read the list of input files from.")
		String input_file = null;
		
		@Parameter(names = {"-r", "--color"}, description =
				"Print colored output.")
		boolean color = false;
		
		@Parameter(names = {"-d", "--debug"}, description =
				"Print additional debug output (affected by --color as well).")
		boolean debug = false;
		
		@Parameter(names = {"-n", "--only-failed"}, description =
				"Print only failed test cases.")
		boolean only_failed = false;
	}
	
	public static void main(String[] args) throws Exception
	{
		var arguments = new Arguments();
		var jcommander = JCommander.newBuilder().addObject(arguments).build();
		jcommander.parse(args);
		
		if (arguments.help)
		{
			jcommander.usage();
			System.out.println("    " +
					"If neither -i nor -f is provided then the list of " +
					"validated files is read from the standard input.");
			return;
		}
		
		if (arguments.config_file == null)
		{
			System.err.println("error: Missing --config file");
			return;
		}
		
		if (arguments.color)
		{
			Package_test.color_decorator = new Ansi_colors.Default_decorator();
		}
		
		try (PrintStream output = arguments.output_file != null ?
				new PrintStream(arguments.output_file) : System.out)
		{
			if (arguments.test_files.isEmpty())
			{
				InputStream is = arguments.input_file != null ?
						new FileInputStream(arguments.input_file) : System.in;
				
				try (var br = new BufferedReader(new InputStreamReader(is)))
				{
					br.lines().forEach((filename) ->
					{
						var path = Paths.get(filename);
						
						if (! path.isAbsolute())
						{
							filename = Paths.get(arguments.input_file).getParent().resolve(path).toString();
						}
						
						arguments.test_files.add(filename);
					});
				}
			}
			
			final var config = new Config(new FileInputStream(arguments.config_file));
			
			/// The union of file paths present in all RPM files
			var files = new TreeSet<String>();
			
			/// The map of symbolic link names to their targets present in all RPM files
			var symlinks = new TreeMap<String, String>();
			var test_results = new ArrayList<Test_result>();
			
			for (final String filename : arguments.test_files)
			{
				final var rpm_path = Paths.get(filename).toAbsolutePath().normalize();
				final String rpm_name = rpm_path.getFileName().toString();
				final var rpm_info = new RpmInfo(rpm_path);
				
				var applicable_rules = new ArrayList<Rule>();
				
				{
					Rule exclusive_rule = null;
					
					for (var rule : config.rules())
					{
						if (rule.applies(rpm_info))
						{
							if (rule.exclusive)
							{
								exclusive_rule = rule;
								break;
							}
							
							applicable_rules.add(rule);
						}
					}
					
					if (exclusive_rule != null)
					{
						applicable_rules.clear();
						applicable_rules.add(exclusive_rule);
					}
				}
				
				/// Prefix every message with the RPM file name
				for (var rule : applicable_rules)
				{
					var results = rule.apply(rpm_path);
					
					for (var tr : results)
					{
						tr.prefix(rpm_name + ": ");
						test_results.add(tr);
					}
				}
				
				final var applicable_jar_validators = applicable_rules.stream()
						.map((r) -> r.jar_validator)
						.filter((jc) -> jc != null)
						.collect(Collectors.toCollection(ArrayList::new));
				
				try (final var rpm_is = new RpmArchiveInputStream(rpm_path))
				{
					CpioArchiveEntry rpm_entry;
					while ((rpm_entry = rpm_is.getNextEntry()) != null)
					{
						String rpm_entry_name = rpm_entry.getName();
						
						if (rpm_entry_name.startsWith("./"))
						{
							rpm_entry_name = rpm_entry_name.substring(1);
						}
						
						files.add(rpm_entry_name);
						
						var content = new byte[(int) rpm_entry.getSize()];
						rpm_is.read(content);
						
						if (rpm_entry.isSymbolicLink())
						{
							var target = new String(content, "UTF-8");
							target = Paths.get(rpm_entry_name).getParent().resolve(Paths.get(target)).normalize().toString();
							symlinks.put(rpm_entry_name, target);
						}
						else
						{
							if (rpm_entry.getName().endsWith(".jar"))
							{
								final String jar_name = rpm_entry_name;
								
								try (var jar_stream = new JarArchiveInputStream(
										new ByteArrayInputStream(content)))
								{
									for (var jv : applicable_jar_validators)
									{
										jv.accept(new Jar_validator.Visitor()
										{
											@Override
											public void visit(Test_result result, String entry)
											{
												result.prefix(entry + ": ");
												test_results.add(result);
											}
										}, jar_stream, rpm_name + ": " + jar_name);
									}
								}
							}
						}
					}
				}
			}
			
			for (var pair : symlinks.entrySet())
			{
				String message = color_decorator().decorate("[Symlink]", Ansi_colors.Type.bold) + ": ";
				final boolean result = files.contains(pair.getValue());
				
				message += MessageFormat.format("Symbolic link \"{0}\" points to \"{1}\" ",
						color_decorator.decorate(pair.getKey(), Ansi_colors.Type.cyan),
						color_decorator.decorate(pair.getValue(), Ansi_colors.Type.yellow));
				
				if (result)
				{
					message += "and the target file "
							+ color_decorator.decorate("exists", Ansi_colors.Type.green, Ansi_colors.Type.bold);
				}
				else
				{
					message += "but the target file "
							+ color_decorator.decorate("does not exist", Ansi_colors.Type.red, Ansi_colors.Type.bold);
				}
				
				test_results.add(new Test_result(result, message));
			}
			
			int test_number = test_results.isEmpty() ? 0 : 1;
			
			output.println(MessageFormat.format("{0}..{1}", test_number, Integer.toString(test_results.size())));
			
			for (var tr : test_results)
			{
				if (tr.result && arguments.only_failed)
				{
					continue;
				}
				
				output.println(MessageFormat.format("{0} {1} - {2}",
						(tr.result ? "ok" : "nok"), Integer.toString(test_number), tr.message()));
				
				if (arguments.debug && tr.debug != null)
				{
					output.println("Debug output:");
					output.println(tr.debug.toString());
				}
				
				++test_number;
			}
		}
	}
}