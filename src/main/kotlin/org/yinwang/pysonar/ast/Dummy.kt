package org.yinwang.pysonar.ast

/**
 * dummy node for locating purposes only
 * rarely used
 */
class Dummy(file: String, start: Int, end: Int) : Node(NodeType.DUMMY, file, start, end)
