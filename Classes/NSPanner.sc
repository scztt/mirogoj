// Gesture inside of the north-south speaker rectangle

NSRectGesture {

	*sendSynthDef {
		| servers |
		servers.asArray.do({
			| server |
			SynthDef(\panner, {
				| dur=1 |
				var x, y, width=2;
				var monoSig, leftMono, rightMono, left, right,
					la, lb, lc, ld, ra, rb, rc, rd, n,
					outChans, protoEnv;
					
				protoEnv = Env( 0!15, [999]++(1!13) ).asArray;
				x = EnvGen.kr( Control.names([\x]).ir( protoEnv ), timeScale:dur );
				y = EnvGen.kr( Control.names([\y]).ir( protoEnv ), timeScale:dur );
				//width = EnvGen.kr( Control.names([\width]).ir( protoEnv ), timeScale:dur );
				
				//x = Lag3.kr( MouseX.kr(0,1), 5); y = Lag3.kr( MouseY.kr(0,1), 5 );
				x = x*2-1; y = (y*2-1)*(3/5);
				//width = 2;
				
				//monoSig = LPF.ar( Slew.kr( Trig.kr( Dust.kr(3), 0.1), 10, 3) * WhiteNoise.ar( 2.2 ), 1000);
				monoSig = PlayBuf.ar( 1, ~b_1.bufnum, loop:1);
				#leftMono, rightMono = Pan2.ar( monoSig, x );
				#lc, ld, n, la, lb = PanAz.ar( 5, leftMono, y, width:width, orientation:-0.5 );
				#rc, rd, n, ra, rb = PanAz.ar( 5, rightMono, y, width:width, orientation:-0.5 );
				outChans = [la, lb, lc, ld, ra, rb, rc, rd];
				Out.ar( 100, outChans );
				~g_groups.do({ 
					| group, i |
					group.postln;
					Out.ar( group, outChans[i] );
				});
			}).send(server)
		})
	}

}