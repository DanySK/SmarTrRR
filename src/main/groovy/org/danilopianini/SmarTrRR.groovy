package org.danilopianini

import org.gradle.api.Project
import org.gradle.api.Plugin
import org.gradle.api.artifacts.DependencyResolveDetails

class SmarTrRR implements Plugin<Project> {
    void apply(Project project) {
        project.extensions.create("substitutions", Substitutions)
        project.configurations.all {
            resolutionStrategy {
                /*
                 * This super-fantastic code implements the Holy Grail in Gradle:
                 * it restricts the version ranges transitively.
                 */
                def substitutions = project.substitutions.substitutions
                def depMap = [:]
                eachDependency { DependencyResolveDetails details ->
                    /*
                     * Apply substitutions
                     */
                    def req = details.requested
                    def depId = req.group + ':' + req.name
                    if (substitutions.containsKey(depId)) {
                        def conversion = substitutions.get(depId)
                        def version = [conversion[0], true]
                        def reqversion = [
                            maxVersion(req.version),
                            maxInclusive(req.version)
                        ]
                        if (higherVersion(version, reqversion, true)) {
                            version = conversion[2]
                            println 'Substitution: ' + depId + ':' + req.version + ' --> ' + conversion[1] + ':' + version
                            depId = conversion[1]
                            def substitution = depId + ':' + version
                            details.useTarget(substitution)
                        }
                    }
                    if (depMap.containsKey(depId)) {
                        /*
                         * Possible version conflict
                         */
                        def detailsList = depMap.get(depId)
                        detailsList << details
                        def previousVersion = detailsList[0].requested.version
                        if(previousVersion.startsWith(']')) {
                            previousVersion = previousVersion.replaceFirst(']', '\\(')
                        }
                        if(previousVersion.endsWith('[')) {
                            def s = previousVersion.length() - 1
                            previousVersion = previousVersion.substring(0, s) + ')'
                        }
                        def newVersion = req.version
                        if(!(previousVersion == newVersion)) {
                            def selectedVersion = resolveConflict(previousVersion, newVersion)
                            println 'Range intersection: ' + depId + ' ' + previousVersion + ' <> ' + newVersion + ' --> ' + selectedVersion
                            detailsList.each { det ->
                                det.useVersion(selectedVersion)
                            }
                        }
                    } else {
                        depMap.put(depId, [details])
                    }
                }
            }
        }
    }

    def splitRange(String version) {
        return version.replace(' ','').split(',')
    }
    def minVersion(String version) {
        def versions = splitRange(version)
        if (versions.length > 1) {
            return versions[0].substring(1)
        }
        return versions[0]
    }
    def maxVersion(String version) {
        def versions = splitRange(version)
        if (versions.length > 1) {
            return versions[1].substring(0, versions[1].length() - 1)
        }
        return versions[0]
    }
    def minInclusive(String version) {
        return !version.startsWith('(') && !version.startsWith(']')
    }
    def maxInclusive(String version) {
        return !version.endsWith(')') && !version.endsWith('[')
    }
    def convertToNumber(String n) {
        if(n.isEmpty() || n.equals('+')) {
            return Long.MAX_VALUE
        }
        try {
            return Long.parseLong(n)
        } catch (NumberFormatException e) {
            return -1
        }
    }
    def higherVersion(def v1, def v2, def inclusiveIsBigger) {
        /*
         * Look at version numbers. If one version is not a number,
         * then the numbered version has priority (e.g. 2.7 > 2.7-rc0).
         * If none is a number, fall back to lexicographical comparison.
         * 2 > 1
         * 2.0 > 2.0-r1
         * 2.0-rc2 > 2.0-rc1
         * A > B
         * 1.0-rc1 > r02
         * 1.r02 > 1.0r02
         * 1.0.0 > 1.0
         * In case of equality, returns true.
         */
        def v1v = v1[0]
        def v2v = v2[0]
        def v1i = v1[1]
        def v2i = v2[1]
        if (v1v == v2v) {
            if (v1i == v2i) {
                return true
            }
            return inclusiveIsBigger ? v1i : v2i
        }
        if (v1v.trim().isEmpty()) {
            return true
        }
        if (v2v.trim().isEmpty()) {
            return false
        }
        def v1parts = v1v.split('\\.')
        def v2parts = v2v.split('\\.')
        for (i in 0..Math.min(v1parts.length, v2parts.length) - 1) {
            if (v1parts[i] != v2parts[i]) {
                def p1 = convertToNumber(v1parts[i])
                def p2 = convertToNumber(v2parts[i])
                if (p1 > p2) {
                    return true
                }
                if (p2 > p1) {
                    return false
                }
                /*
                 * They are different Strings, fall back to lexicographical comparison
                 */
                return v1parts[i] >= v2parts[i]
            }
        }
        /*
         * Exactly the same version, only in: verify inclusion.
         * NOTE: in case of minimum required dependency,
         * the returned result is wrong.
         */
        if (v1parts.length == v2parts.length) {
            println '========================================'
            println 'ERROR IN RANGE RESOLVER. FIX ME, MASTER!'
            println 'Arg 1: ' + v1
            println 'Arg 2: ' + v2
            println 'Arg 3: ' + inclusiveIsBigger
            println '========================================'
        }
        return v1parts.length >= v2parts.length
    }
    def minVersionToInfo(def v) {
        return [
            minVersion(v),
            minInclusive(v)
        ]
    }
    def maxVersionToInfo(def v) {
        return [
            maxVersion(v),
            maxInclusive(v)
        ]
    }
    def getMaxMin(def v1, def v2) {
        def min1 = minVersionToInfo(v1)
        def min2 = minVersionToInfo(v2)
        if (higherVersion(min1, min2, false)) {
            return min1
        }
        return min2
    }
    def getMinMax(def v1, def v2) {
        def max1 = maxVersionToInfo(v1)
        def max2 = maxVersionToInfo(v2)
        if (higherVersion(max2, max1, true)) {
            return max1
        }
        return max2
    }
    def resolveConflict(def v1, def v2) {
        def maxmin = getMaxMin(v1, v2)
        def minmax = getMinMax(v1, v2)
        if (higherVersion(maxmin, minmax, true) || higherVersion(maxmin, minmax, false)) {
            /*
             * Either there is a single compatible version, or there is a conflict.
             * In case of conflict, the resolution strategy is to force the minimum
             * compatible version with the artifact that is most restrictive. The hope
             * is that the other artifact can work with that version.
             */
            if (!maxmin.equals(minmax)) {
                println '[WARNING] conflict: no intersection between ' + v1 + ' and ' + v2 + '. Forcing ' + maxmin[0] + ' and hoping for the best.'
            }
            return maxmin[0]
        }
        return (maxmin[1] ? '[' : '(') + maxmin[0] + ', ' + minmax[0] + (minmax[1] ? ']' : ')')
    }
}

class Substitutions {
    def Map substitutions = [:]
    
    def substitute(def String origin) {
        [up_to: { String maxVersion=Integer.toString(Integer.MAX_VALUE) ->
            [with: { String destination ->
                [at: { String newVersion = '+'
                    substitutions.put(origin, [maxVersion, destination, newVersion])             
                }]
            }]
        }]
    }
    
}
