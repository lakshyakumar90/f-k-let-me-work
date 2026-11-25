//go:build !windows

package main

func launchDashboard() bool        { return false }
func launchWorker(_ []string) bool { return false }
