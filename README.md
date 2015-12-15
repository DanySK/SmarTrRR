# SmarTrRR
> Read: "smarter"

A Smarter Transitive Dependency Range Resolver for Gradle

## Description

### The problem

Gradle by default resolves each dependency range, then works at resolving conflicts. This, however, may be a source of headaches.

Imagine that your Gradle-built software depends on ``foo:bar:1.0``, that in turn depends on ``com.google.guava:[15.0,17.0]``.  Also, you need the famous ``baz:qux:2.1`` library, that instead requires ``com.google.guava:[16.0, 19.0-rc2]``. What does Gradle dependency resolver do for you?

1. Resolves ``com.google.guava:[15.0,17.0]``, and picks ``com.google.guava:17.0``
2. Resolves ``com.google.guava:[16.0,19.0-rc2]``, and picks ``com.google.guava:19.0-rc2``
3. Uh-oh... that was unexpected: now we have a conflict between version ``17.0`` and version ``19.0-rc2``.

The user does not normally notice that, because the default conflict resolution strategy is "pick the latest", so ``19.0-rc2`` is selected silently, and the build goes on.
But what if ``foo:bar:1.0`` can not work with ``com.google.guava:19.0-rc2``? You end up with a broken build, that might even fail at runtime.
To prevent this horrible situation, what you can do is to change the behavior of the Gradle conflict resolver, and call ``failOnVersionConflict()``. This makes the build fail anytime there is a conflict, and the user can manually fix it by carefully add dependency substitutions or forced dependencies. Yes, **manually**.

You surely noticed that there is an overlap between ``[15.0,17.0]`` and ``[16.0, 19.0-rc2]``. What most people want to do in such case is to resolve ``[16.0, 17.0]``, and pick the highest available version that is compatible with the range.

Unfortunately, Gradle has no built in mechanism to do so.

### The solution

This is where SmarTrRR (SMARter TRansitive Range Resolver) comes in to play. This script overrides the project's resolution strategy and behaves as follow:
* Apply the substitutions (in case you want to rename some transitive dependencies and point them elsewhere, e.g. to translate ``com.google.guava:guava:14.0.1`` into ``com.google.guava:guava-jdk5:14.0.1``)
* In case of range overlap, compute the intersection range. Force the intersection range to the current and all the previously scanned dependencies
* In case of pointwise intersection (namely, a single version is compatible), pick such version
* In case of actual version conflict, pick the highest lower-compatible version. For instance, if there is a conflict between ``[2.0, 3[`` and ``[3.0, 5[``, then ``3.0`` is selected. As another example, in case of conflict between ``1.2`` and ``1.3``, ``1.3`` is selected (this is similar to the default Gradle behavior).

## Use

### Use SmarTrRR to resolve transitive dependency ranges

Very little effort is required to enable SmarTrRR in your Gradle build:

```gradle
buildscript {
    repositories {
        mavenCentral()
    }
    dependencies {
        classpath 'org.danilopianini:smartrrr:0.0.1'
    }
}
apply plugin: 'org.danilopianini.smartrrr'
```

That's it: the plugin will be downloaded from Central, brought in into your build classpath, and used for dependency resolution. Yay!

### Configure dependency substitutions

Substitutions are used to point **all** the instances of some artifact *up to certain version* to another artifact, or to another version of the same one. A ``substitutions`` section in your ``build.gradle`` will do the job.

Here is an example: 

```gradle
substitutions {
	substitute 'asm:asm' up_to '6' with 'org.ow2.asm:asm' at '+'
	substitute 'com.google.guava:guava' up_to '14.0.1' with 'com.google.guava:guava-jdk5' at '+'
}
```

This configuration substitutes any artifact matching ``asm:asm:[0, 6]`` with ``org.ow2.asm:asm:+`` and any artifact matching ``com.google.guava:guava:[0, 14.0.1]``  with ``com.google.guava:guava-jdk5:+``

## Notes for Developers

### Importing the project
The project has been developed using Eclipse, and can be easily imported in such IDE.

#### Building the project
While developing, you can rely on Eclipse to build the project, it will generally do a very good job.
If you want to generate the artifacts, you can rely on Gradle. Just point a terminal on the project's root and issue

```bash
./gradlew
```

This will trigger the creation of the artifacts the executions of the tests, the generation of the documentation and of the project reports.

#### Release numbers explained
We release often. We are not scared of high version numbers, as they are just numbers in the end.
We use a three level numbering, following the model of [Semantic Versioning][SemVer]:

* **Update of the minor number**: there are some small changes, and no backwards compatibility is broken. More particularly, it should be the case that any project that depends on this one should have no problem compiling or running. Raise the minor version if there is just a bug fix or a code improvement, such that no interface, constructor, or non-private member of a class is modified either in syntax or in semantics. Also, no new classes should be provided.
	* Example: switch from 1.2.3 to 1.2.4 after improving how an error condition is reported 
* **Update of the middle number**: there are changes that should not break any backwards compatibility, but the possibility exists. Raise the middle version number if there is a remote probability that projects that depend upon this one may have problems compiling if they update. For instance, if you have added a new class, since a depending project may have already defined it, that is enough to trigger a mid-number change. Also updating the version ranges of a dependency, or adding a new dependency, should cause the mid-number to raise. As for minor numbers, no changes to interfaces, constructors or non-private member of classes are allowed, except for adding *new* methods. If mid-number is update, minor number should be reset to 0.
	* Example: switch from 1.2.3 to 1.3.0 after adding new methods for computing with Tuples.
* **Update of the major number**: *non-backwards-compatible change*. If a change in interfaces, constructors, or public member of some class has happened, a new major number should be issued. This is also the case if the semantics of some method has changed. In general, if there is a high probability that projects depending upon this one may experience compile-time or run-time issues if they switch to the new version, then a new major number should be adopted. If the major version number is upgraded, the mid and minor numbers should be reset to 0.
	* Example: switch from 1.2.3 to 2.0.0 after changing the interface for parsing programs.

[SemVer]: http://semver.org/spec/v2.0.0.html
