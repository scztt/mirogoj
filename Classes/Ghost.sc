Ghost {
	var server;
	var <inBus, <writer, <historyBuffer, <ghostSynth, <ghostBus, <>bufferInput;
	
	*newFromBus {
		| bus, duration, server |
		^super.newCopyArgs( server ).init( bus, duration )
	}
	
	*newFromBuffer {
		| buffer, duration, server |
		var newGhost;
		var proxy;
		buffer.bufnum.postln;
		proxy = NodeProxy.new( server, \audio, 1 );
/*		buffer.doOnInfo = {
			|buffer|
			proxy.source = { Mix( PlayBuf.ar( buffer.numChannels, buffer.bufnum, loop:0 ) ) };
		};
*/		newGhost = this.newFromBus( proxy.bus, duration, server );
//		newGhost.bufferInput = proxy;
		newGhost.bufferInput = buffer;
		^newGhost
	}
	
	*ghostSynthDef {
		^SynthDef( \ghost, {
			| out, amp=0, width=1, density=0.8, rate=1, rateDev=0.3, fadeTime=0, buffer | 
			var pos, ghostPos, ghosts, freqs, widths;
			
			SeedMaster.ugen;
			width = ControlFade.kr( width, fadeTime );
			density = ControlFade.kr( density, fadeTime ).max(0.1);
			amp = ControlFade.kr( amp, fadeTime );
			rateDev = ControlFade.kr( rateDev, fadeTime );	
			rate = ControlFade.kr( rate, fadeTime );	
			
			pos = LFNoise2.kr( 0.05, 0.5, 0.5 )*BufDur.kr(buffer);
			ghostPos = pos + ({ LFNoise2.kr( LFNoise2.kr( 0.1, 0.2 ), 5 ) }!5);
			ghosts = ghostPos.collect({
				| p |
				TGrains.ar( 2, Dust.kr(density), buffer, 
					centerPos:p, 
					rate:LFNoise1.kr(1, rateDev, rate), 
					dur:5, 
					pan:LFNoise1.kr(4), 
					interp:4 );
			});
			freqs = Mirror.kr( ( { LFNoise2.kr( 0.03, 5000, 6000 ) }!5 ), 100, 15000);
			widths = { LFNoise2.kr( 0.1, 300*width, 500*width ) }!5 + 50;
			ghosts = ghosts.collect({
				|ghost, i|
				BPF.ar( ghost, freqs[i], widths[i]/freqs[i] );
			});
			Out.ar( out, Mix( ghosts*0.5*amp ) );
		})
	}
	
	*writerSynthDef {
		^SynthDef( \ghostWriter, {
			| bus, buffer |
			var rec = LFDNoise3.kr( LFDNoise3.kr(0.1, 0.1), 0.5, 0.5);
			SeedMaster.ugen;
			RecordBuf.ar( InFeedback.ar(bus), buffer, recLevel:rec, preLevel:(1-rec)/2, loop:1 );
		})
	}
	
	init {
		| bus, duration |
		inBus = bus;
		duration = duration ? 60;
		historyBuffer = Buffer.alloc( server, duration*server.sampleRate );
		this.class.ghostSynthDef.send(server);
		this.class.writerSynthDef.send(server);
	}

	play {
		| ...args |
		ghostBus = Bus.alloc( \audio, server, 2 );
//		writer = Synth.new( \ghostWriter, [\bus, inBus, \buffer, historyBuffer],
//			target: GroupManager.get(server).listener );
		ghostSynth = Synth.new( \ghost, 
			[\randSeed, SeedMaster.seed, \out, ghostBus, \buffer, bufferInput.bufnum] ++ args,
			target: GroupManager.get(server).synths );
	}
	
	free {
		ghostBus.free;
		writer.free;
		ghostSynth.free;
		bufferInput.notNil.if({ bufferInput.free.clear }); 

		ghostBus = nil;
		writer = nil;
		ghostSynth = nil;
		bufferInput = nil; 
	}
	
	bus {
		^ghostBus
	}
	
	plot {
		historyBuffer.plot;	
	}
	
	set {
		| ...args |
		ghostSynth.set( *args );
	}
	
	inScope {
		inBus.scope;
	}
	
	outScope {
		ghostBus.scope;
	}
	
	newElementPanner {
		^ElementPanner.new( this.ghostBus );
	}
	
	listen {
		ghostSynth.notNil.if({ ghostSynth.set( \out, 0 ) });
	}
	
	resetBuffer {
		historyBuffer.zero;
	}
	
	stopListening {
		ghostSynth.notNil.if({ ghostSynth.set( \out, ghostBus ) })	
	}
}